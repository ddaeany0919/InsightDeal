Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "c:\Users\kth00\StudioProjects\InsightDeal"
WshShell.Run "cmd.exe /c python -m backend.scheduler.cloud_feeder --one-shot > backend\logs\cloud_feeder.log 2>&1", 0, True
