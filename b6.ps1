Set-Location E:\rootlmai
.\gradlew compileJava --rerun-tasks --no-daemon 2>&1 | Tee-Object -FilePath compile_final.log | Select-Object -Last 25
