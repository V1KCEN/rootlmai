Set-Location E:\rootlmai
$mcargs = '--quickPlayMultiplayer localhost:25565'
.\gradlew.bat runClient --no-daemon --args="$mcargs" 2>&1 | Tee-Object -FilePath client_conn2.log | Out-Null
