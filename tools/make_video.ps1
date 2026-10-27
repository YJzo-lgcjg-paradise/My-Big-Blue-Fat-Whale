# Build narration video: TTS + ffmpeg (ASCII-only on purpose: script encoding safety)
$ErrorActionPreference = "Stop"

$project = (Get-ChildItem "C:\Users\user\Desktop" -Directory |
    Where-Object { $_.Name -like "My Big Blue Fat Whale*Forever*" } |
    Select-Object -First 1).FullName
$dir = Join-Path $project "build\video"
$manifest = Get-Content (Join-Path $dir "manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SelectVoice("Microsoft Huihui Desktop")
$synth.Rate = 0
$synth.Volume = 100

$ffmpeg = (Get-Command ffmpeg).Source
$segments = @()

foreach ($s in $manifest) {
    $idx = "{0:D2}" -f [int]$s.index
    $wav = Join-Path $dir "voice-$idx.wav"
    $mp4 = Join-Path $dir "seg-$idx.mp4"

    $synth.SetOutputToWaveFile($wav)
    $synth.Speak([string]$s.text)
    $synth.SetOutputToNull()

    & $ffmpeg -y -loglevel error -loop 1 -framerate 30 -i $s.image -i $wav `
        -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -vf "scale=1280:720" `
        -c:a aac -b:a 128k -shortest -movflags +faststart $mp4
    $segments += $mp4
    Write-Host ("seg {0} ok" -f $idx)
}

$list = Join-Path $dir "concat.txt"
$lines = $segments | ForEach-Object { "file '" + ($_ -replace '\\', '/') + "'" }
[System.IO.File]::WriteAllLines($list, $lines, (New-Object System.Text.UTF8Encoding($false)))

$outDir = (Get-ChildItem "C:\Users\user\Desktop" -Directory |
    Where-Object { $_.Name -eq "My Big Blue Fat Whale 3.0" } |
    Select-Object -First 1).FullName
$out = Join-Path $outDir "3.0 ship.mp4"

& $ffmpeg -y -loglevel error -f concat -safe 0 -i $list -c copy -movflags +faststart $out
if (Test-Path $out) {
    $mb = [math]::Round((Get-Item $out).Length / 1MB, 1)
    Write-Host ("done: {0}  {1} MB" -f $out, $mb)
} else {
    Write-Host "concat failed"
}
