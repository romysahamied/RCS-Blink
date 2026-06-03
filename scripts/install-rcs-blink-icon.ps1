# Generates web + Android launcher assets from branding/rcs-blink-icon.png (1024x1024)
param(
    [string]$Source = "$PSScriptRoot\..\branding\rcs-blink-icon.png"
)

Add-Type -AssemblyName System.Drawing

function Save-Resize {
    param(
        [System.Drawing.Image]$Image,
        [int]$Size,
        [string]$Path
    )
    $dir = Split-Path $Path -Parent
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($Image, 0, 0, $Size, $Size)
    $g.Dispose()
    $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

if (-not (Test-Path $Source)) {
    Write-Error "Source not found: $Source"
    exit 1
}

$root = Resolve-Path "$PSScriptRoot\.."
$img = [System.Drawing.Image]::FromFile((Resolve-Path $Source))

# Web
Save-Resize $img 512 "$root\web\public\images\logo.png"
Save-Resize $img 512 "$root\web\public\images\rcs-blink-icon.png"
Save-Resize $img 512 "$root\web\public\icon.png"
Save-Resize $img 512 "$root\web\app\icon.png"
Save-Resize $img 180 "$root\web\app\apple-icon.png"
Save-Resize $img 32 "$root\web\public\favicon-32x32.png"
Save-Resize $img 16 "$root\web\public\favicon-16x16.png"

# Android launcher (legacy)
$launcherSizes = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($folder in $launcherSizes.Keys) {
    $size = $launcherSizes[$folder]
    $base = "$root\android\app\src\main\res\$folder"
    Get-ChildItem $base -Filter "ic_launcher*.webp" -ErrorAction SilentlyContinue | Remove-Item -Force
    Save-Resize $img $size "$base\ic_launcher.png"
    Save-Resize $img $size "$base\ic_launcher_round.png"
}

# Android adaptive foreground (108dp base)
$foregroundSizes = @{
    'mipmap-mdpi'    = 108
    'mipmap-hdpi'    = 162
    'mipmap-xhdpi'   = 216
    'mipmap-xxhdpi'  = 324
    'mipmap-xxxhdpi' = 432
}
foreach ($folder in $foregroundSizes.Keys) {
    $size = $foregroundSizes[$folder]
    $base = "$root\android\app\src\main\res\$folder"
    Save-Resize $img $size "$base\ic_launcher_foreground.png"
}

# In-app header + notifications (drawable)
Save-Resize $img 192 "$root\android\app\src\main\res\drawable\rcs_blink_icon.png"
Save-Resize $img 96 "$root\android\app\src\main\res\drawable\ic_notification.png"

$img.Dispose()
Write-Host "RCS Blink icon installed successfully."
