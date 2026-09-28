param([switch]$Reboot)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$adb = Join-Path $root '.toolchain/android-sdk/platform-tools/adb.exe'
$serial = 'emulator-5554'
$database = '/data/data/com.campusflow.app/databases/campusflow.db'
$report = Join-Path $root 'verification'
function Invoke-Adb([string[]]$Arguments) {
    $output = & $adb -s $serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments" }
    return $output
}
if ((Invoke-Adb @('shell','getprop','ro.kernel.qemu')).Trim() -ne '1') { throw 'This verification only runs on the project emulator.' }
Invoke-Adb @('install','-r', (Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('shell','pm','grant','com.campusflow.app','android.permission.POST_NOTIFICATIONS')
Invoke-Adb @('shell','appops','set','com.campusflow.app','SCHEDULE_EXACT_ALARM','allow')
Invoke-Adb @('shell','am','start','-W','-n','com.campusflow.app/.MainActivity')
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME')
Invoke-Adb @('shell','am','kill','com.campusflow.app')
$initialProcess = (& $adb -s $serial shell pidof com.campusflow.app | Out-String).Trim()
if ($initialProcess) { Invoke-Adb @('shell','kill','-9',$initialProcess) }
$epoch = [long](Invoke-Adb @('shell','date','+%s')).Trim()
$start = [DateTimeOffset]::FromUnixTimeSeconds($epoch).AddMinutes($(if ($Reboot) { 4 } else { 2 }))
$day = [long][math]::Floor($start.ToUnixTimeSeconds() / 86400)
$minute = $start.Hour * 60 + $start.Minute
$name = if ($Reboot) { "CampusFlow Reboot Verification $epoch" } else { "CampusFlow Background Verification $epoch" }
$sql = ".timeout 30000`nPRAGMA foreign_keys=ON; DELETE FROM schedules WHERE notes='Test fixture' AND title LIKE 'CampusFlow % Verification%'; INSERT INTO schedules(title,category,startDay,startMinute,durationMinutes,repeat,weekdays,endDay,reminderMinutes,notes,subjectId,routineId,active) VALUES('$name','PERSONAL',$day,$minute,30,'ONCE','',NULL,0,'Test fixture',NULL,NULL,1); SELECT last_insert_rowid();"
$sql | Set-Content (Join-Path $report 'alarm-fixture.sql') -Encoding ASCII
Invoke-Adb @('push',(Join-Path $report 'alarm-fixture.sql'),'/data/local/tmp/campusflow-fixture.sql')
$id = [long](Invoke-Adb @('shell',"sqlite3 '$database' < /data/local/tmp/campusflow-fixture.sql")).Trim()
Invoke-Adb @('shell','am','start','-W','-n','com.campusflow.app/.MainActivity')
$alarmDeadline = (Get-Date).AddSeconds(30)
do {
    $alarms = Invoke-Adb @('shell','dumpsys','alarm')
    if (($alarms -join "`n") -match 'com.campusflow.app/.reminders.ReminderReceiver') { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $alarmDeadline)
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME')
Invoke-Adb @('shell','am','kill','com.campusflow.app')
$process = (& $adb -s $serial shell pidof com.campusflow.app | Out-String).Trim()
if ($process) {
    Start-Sleep -Seconds 2
    Invoke-Adb @('shell','am','kill','com.campusflow.app')
    $process = (& $adb -s $serial shell pidof com.campusflow.app | Out-String).Trim()
}
if ($process -and !$Reboot) {
    Invoke-Adb @('shell','kill','-9',$process)
    $process = (& $adb -s $serial shell pidof com.campusflow.app | Out-String).Trim()
    if ($process) { throw "Application process still running: $process" }
}
if ($Reboot) {
    Invoke-Adb @('reboot')
    Invoke-Adb @('wait-for-device')
    Invoke-Adb @('root')
    Invoke-Adb @('wait-for-device')
    $bootDeadline = (Get-Date).AddMinutes(2)
    do {
        $boot = (Invoke-Adb @('shell','getprop','sys.boot_completed')).Trim()
        if ($boot -eq '1') { break }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $bootDeadline)
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP')
    Invoke-Adb @('shell','wm','dismiss-keyguard')
}
$deadline = (Get-Date).AddMinutes($(if ($Reboot) { 5 } else { 3 }))
$found = $false
do {
    $notifications = Invoke-Adb @('shell','dumpsys','notification','--noredact')
    if (($notifications -join "`n").Contains("android.title=String ($name)")) { $found = $true; break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
$suffix = if ($Reboot) { 'reboot' } else { 'background' }
$notifications | Set-Content (Join-Path $report "$suffix-notifications.txt") -Encoding UTF8
if (!$found) { throw 'Expected notification was not delivered before the deadline.' }
"PASS: $name. UI closed; application process killed or device rebooted before delivery. Schedule ID $id. Device time: $((Invoke-Adb @('shell','date')) -join ' ')." | Tee-Object -FilePath (Join-Path $report "$suffix-result.txt")
Invoke-Adb @('shell','cmd','statusbar','expand-notifications')
Invoke-Adb @('shell','uiautomator','dump','/sdcard/campusflow-notification-ui.xml')
Invoke-Adb @('shell','screencap','-p',"/sdcard/$suffix.png")
Invoke-Adb @('pull',"/sdcard/$suffix.png",(Join-Path $report "$suffix.png"))
".timeout 30000`nPRAGMA foreign_keys=ON; DELETE FROM schedules WHERE id=$id;" | Set-Content (Join-Path $report 'alarm-cleanup.sql') -Encoding ASCII
Invoke-Adb @('push',(Join-Path $report 'alarm-cleanup.sql'),'/data/local/tmp/campusflow-cleanup.sql')
Invoke-Adb @('shell',"sqlite3 '$database' < /data/local/tmp/campusflow-cleanup.sql")
Invoke-Adb @('shell','cmd','statusbar','collapse')
