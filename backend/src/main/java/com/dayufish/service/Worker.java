package com.dayufish.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台整理队列：每 2 秒看一眼有没有待总结的记忆，有就处理几条。
 * 这样导入接口可以立刻返回，不用等大模型把几十条都写完。
 */
@Component
public class Worker {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    private final MemoryService memory;
    private final EpisodeService episodes;
    private final QwenOcrService ocr;

    public Worker(MemoryService memory, EpisodeService episodes, QwenOcrService ocr) {
        this.memory = memory;
        this.episodes = episodes;
        this.ocr = ocr;
    }

    @Scheduled(fixedDelay = 2000L, initialDelay = 3000L)
    public void tick() {
        try {
            if (memory.pendingCount() > 0) {
                int done = memory.drain(10);
                if (done > 0) {
                    log.info("整理完成 {} 条，剩余 {} 条", done, memory.pendingCount());
                }
            }
            try {
                if (ocr.pendingCount() > 0) {
                    ocr.drain(2);
                }
            } catch (Exception e) {
                log.warn("图片识别异常：{}", e.getMessage());
            }
            // 事件层：没划分的先全量划分，其余攒批归类；日记排队重写
            try {
                episodes.tickAll();
                if (episodes.hasPendingDiary()) {
                    episodes.drainDiary(null, 2);
                }
            } catch (Exception e) {
                log.warn("事件层处理异常：{}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("后台整理异常：{}", e.getMessage());
        }
    }
}
