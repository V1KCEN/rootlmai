$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location E:\rootlmai
.\gradlew clean build --no-daemon 2>&1 | Out-File -FilePath build_clean.log -Encoding utf8
