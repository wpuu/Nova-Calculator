Add-Type -AssemblyName System.Drawing
$imgPath = 'C:\Users\Admin\.gemini\antigravity-ide\brain\110d569c-310a-43f5-8dca-995bca5be5c4\media__1779690753393.png'
$img = [System.Drawing.Image]::FromFile($imgPath)

$sizes = @(
    @{name='mdpi'; size=48},
    @{name='hdpi'; size=72},
    @{name='xhdpi'; size=96},
    @{name='xxhdpi'; size=144},
    @{name='xxxhdpi'; size=192}
)

foreach ($s in $sizes) {
    $bmp = New-Object System.Drawing.Bitmap($s.size, $s.size)
    $graph = [System.Drawing.Graphics]::FromImage($bmp)
    $graph.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graph.DrawImage($img, 0, 0, $s.size, $s.size)
    
    $path1 = "E:\Nova Calculator\app\src\main\res\drawable-\ic_launcher.png"
    $path2 = "E:\Nova Calculator\app\src\main\res\drawable-\ic_launcher_window.png"
    
    $bmp.Save($path1, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($path2, [System.Drawing.Imaging.ImageFormat]::Png)
    $graph.Dispose()
    $bmp.Dispose()
}
$img.Dispose()
