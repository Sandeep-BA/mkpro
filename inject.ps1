$file = "src/main/java/com/mkpro/Maker.java"
$content = Get-Content $file -Raw
if (-not $content.Contains("public static String backItUp(File file)")) {
    Write-Host "Not found"
} else {
    Write-Host "Found"
}
$file = "src/main/java/com/mkpro/Maker.java"
