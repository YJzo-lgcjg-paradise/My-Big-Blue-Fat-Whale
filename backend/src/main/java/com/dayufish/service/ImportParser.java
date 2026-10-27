package com.dayufish.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 把粘贴进来的对话切成「一问一答」。
 * 兼容：用户：/ 助手：  User: / Assistant:  我：/ AI：  问：/ 答：  ### 用户  [2026-10-20 14:33]
 */
@Component
public class ImportParser {

    /** 一条对话里带的图片（DSH 的附件引用） */
    public record Media(String attachmentId, String mime, String name) {
    }


    private static final Pattern USER_LINE = Pattern.compile(
            "^\\s*[*#>\\-\\s]*(用户|我|提问|问|user|human|q)\\s*[*]*\\s*[:：]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AI_LINE = Pattern.compile(
            "^\\s*[*#>\\-\\s]*(助手|回答|答|ai|assistant|deepseek|模型|bot)\\s*[*]*\\s*[:：]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAD_USER = Pattern.compile(
            "^\\s*#{1,6}\\s*(用户|我|问|user|human)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAD_AI = Pattern.compile(
            "^\\s*#{1,6}\\s*(助手|回答|答|ai|assistant|deepseek)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern STAMP_FULL = Pattern.compile(
            "(\\d{4})\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})\\s*日?(?:\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2}))?");
    private static final Pattern STAMP_MD = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*(?:\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2}))?");

    public record Turn(String question, String answer, Instant occurredAt) {
    }

    public record Parsed(List<Turn> turns, int blocks) {
    }

    private record Block(String role, String text, Instant stamp) {
    }

    public Parsed parse(String text) {
        String src = text == null ? "" : text.replace("\r\n", "\n");
        List<Block> blocks = new ArrayList<>();
        boolean inBlock = false;
        Instant stamp = null;

        for (String line : src.split("\n", -1)) {
            Instant found = parseStamp(line);
            if (found != null) {
                stamp = found;
                if (isOnlyStamp(line)) {
                    continue;
                }
            }

            Matcher mu = USER_LINE.matcher(line);
            Matcher ma = AI_LINE.matcher(line);

            if (HEAD_USER.matcher(line).matches() || mu.matches()) {
                blocks.add(new Block("user", mu.matches() ? mu.group(2) : "", stamp));
                inBlock = true;
                continue;
            }
            if (HEAD_AI.matcher(line).matches() || ma.matches()) {
                blocks.add(new Block("ai", ma.matches() ? ma.group(2) : "", stamp));
                inBlock = true;
                continue;
            }
            if (inBlock && !blocks.isEmpty()) {
                Block last = blocks.remove(blocks.size() - 1);
                String merged = last.text().isEmpty() ? line : last.text() + "\n" + line;
                blocks.add(new Block(last.role(), merged, last.stamp()));
            } else if (!line.isBlank()) {
                blocks.add(new Block("user", line, stamp));
                inBlock = true;
            }
        }

        List<Turn> turns = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (!"user".equals(b.role())) {
                continue;
            }
            Block next = i + 1 < blocks.size() ? blocks.get(i + 1) : null;
            if (next != null && "ai".equals(next.role())) {
                turns.add(new Turn(b.text().trim(), next.text().trim(), b.stamp()));
                i++;
            } else {
                turns.add(new Turn(b.text().trim(), "", b.stamp()));
            }
        }

        if (turns.isEmpty() && !src.isBlank()) {
            String t = src.trim();
            int cut = t.indexOf('\n');
            turns.add(new Turn(
                    cut > 0 ? t.substring(0, cut) : t.substring(0, Math.min(120, t.length())),
                    cut > 0 ? t.substring(cut + 1) : t,
                    null));
        }

        Instant base = Instant.now();
        List<Turn> filled = new ArrayList<>(turns.size());
        for (int i = 0; i < turns.size(); i++) {
            Turn t = turns.get(i);
            Instant at = t.occurredAt() != null
                    ? t.occurredAt()
                    : base.minusSeconds((long) (turns.size() - i) * 6 * 60);
            String q = t.question().length() > 4000 ? t.question().substring(0, 4000) : t.question();
            String a = t.answer().length() > 8000 ? t.answer().substring(0, 8000) : t.answer();
            filled.add(new Turn(q, a, at));
        }
        return new Parsed(filled, blocks.size());
    }

    private Instant parseStamp(String line) {
        Matcher m = STAMP_FULL.matcher(line);
        if (m.find()) {
            return toInstant(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    m.group(4) == null ? 9 : Integer.parseInt(m.group(4)),
                    m.group(5) == null ? 0 : Integer.parseInt(m.group(5)));
        }
        m = STAMP_MD.matcher(line);
        if (m.find()) {
            return toInstant(
                    LocalDate.now().getYear(),
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    m.group(3) == null ? 9 : Integer.parseInt(m.group(3)),
                    m.group(4) == null ? 0 : Integer.parseInt(m.group(4)));
        }
        return null;
    }

    private Instant toInstant(int y, int mo, int d, int h, int mi) {
        try {
            LocalDateTime ldt = LocalDateTime.of(y, mo, d, h, mi);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isOnlyStamp(String line) {
        String s = STAMP_FULL.matcher(line).replaceAll("");
        s = STAMP_MD.matcher(s).replaceAll("");
        s = s.replaceAll("[\\[\\]()（）:：\\-\\s]", "");
        return s.isEmpty();
    }

    public static String format(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}
