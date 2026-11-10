Set-Location E:\rootlmai
.\gradlew clean build --no-daemon 2>&1 | Tee-Object -FilePath build_final.log | Select-Object -Last 30
