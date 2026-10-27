# 给演示片段配音，并与主视频拼接（ASCII-only 脚本，避免编码问题）
$ErrorActionPreference = "Stop"

$project = (Get-ChildItem "C:\Users\user\Desktop" -Directory |
    Where-Object { $_.Name -like "My Big Blue Fat Whale*Forever*" } |
    Select-Object -First 1).FullName
$dir = Join-Path $project "build\video"
$demoDir = Join-Path $project "build\demo"

$text = Get-Content (Join-Path $demoDir "narration.txt") -Raw -Encoding UTF8

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SelectVoice("Microsoft Huihui Desktop")
$synth.Rate = 0
$voice = Join-Path $demoDir "demo-voice.wav"
$synth.SetOutputToWaveFile($voice)
$synth.Speak($text)
$synth.SetOutputToNull()

$ffmpeg = (Get-Command ffmpeg).Source
$silent = Join-Path $demoDir "demo-silent.mp4"
$demo = Join-Path $demoDir "demo.mp4"

& $ffmpeg -y -loglevel error -i $silent -i $voice `
    -c:v copy -c:a aac -b:a 128k -af "apad" -shortest -movflags +faststart $demo

$list = Join-Path $dir "final.txt"
$segs = @()
for ($i = 0; $i -le 4; $i++) { $segs += (Join-Path $dir ("seg-{0:D2}.mp4" -f $i)) }
$segs += $demo
for ($i = 5; $i -le 9; $i++) { $segs += (Join-Path $dir ("seg-{0:D2}.mp4" -f $i)) }

$lines = $segs | ForEach-Object { "file '" + ($_ -replace '\\', '/') + "'" }
[System.IO.File]::WriteAllLines($list, $lines, (New-Object System.Text.UTF8Encoding($false)))

$outDir = (Get-ChildItem "C:\Users\user\Desktop" -Directory |
    Where-Object { $_.Name -eq "My Big Blue Fat Whale 3.0" } |
    Select-Object -First 1).FullName
$out = Join-Path $outDir "3.0 explain.mp4"

& $ffmpeg -y -loglevel error -f concat -safe 0 -i $list -c copy -movflags +faststart $out
if (Test-Path $out) {
    $mb = [math]::Round((Get-Item $out).Length / 1MB, 1)
    $dur = & (Get-Command ffprobe).Source -v error -show_entries format=duration -of csv=p=0 $out
    Write-Host ("done: {0}  {1} MB  {2}s" -f $out, $mb, [math]::Round([double]$dur, 1))
} else {
    Write-Host "concat failed"
}
