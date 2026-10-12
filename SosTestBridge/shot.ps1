# Screenshot of the game without showing it on the user's screen: restores the window from minimized WITHOUT activating it,
# moves it off the monitor, captures it with PrintWindow (rendered content) and minimizes it again.
# Usage: .\shot.ps1 <file.png>
param([Parameter(Mandatory)][string]$Out)
Add-Type -AssemblyName System.Drawing
Add-Type -Namespace S -Name W -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool ShowWindow(System.IntPtr h, int c);
[DllImport("user32.dll")] public static extern bool SetWindowPos(System.IntPtr h, System.IntPtr after, int x, int y, int cx, int cy, uint f);
[DllImport("user32.dll")] public static extern bool PrintWindow(System.IntPtr h, System.IntPtr dc, uint f);
[DllImport("user32.dll")] public static extern bool GetClientRect(System.IntPtr h, out RECT r);
public struct RECT { public int L, T, R, B; }
'@
$p = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -match 'SongsOfSyx|init\.Main' } |
    ForEach-Object { Get-Process -Id $_.ProcessId } | Where-Object { $_.MainWindowHandle -ne 0 } | Select -First 1
if (-not $p) { throw 'game window not found' }
$h = $p.MainWindowHandle
[S.W]::SetWindowPos($h, [IntPtr]::Zero, -4000, -4000, 0, 0, 0x0001 -bor 0x0004 -bor 0x0010) | Out-Null  # NOSIZE|NOZORDER|NOACTIVATE
[S.W]::ShowWindow($h, 4) | Out-Null   # SW_SHOWNOACTIVATE: restores without stealing focus
Start-Sleep -Milliseconds 1500       # let the game draw a few frames
$r = New-Object S.W+RECT; [S.W]::GetClientRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap ([math]::Max(1, $r.R)), ([math]::Max(1, $r.B))
$g = [System.Drawing.Graphics]::FromImage($bmp); $dc = $g.GetHdc()
[S.W]::PrintWindow($h, $dc, 3) | Out-Null  # PW_CLIENTONLY | PW_RENDERFULLCONTENT
$g.ReleaseHdc($dc); $g.Dispose(); $bmp.Save($Out); $bmp.Dispose()
[S.W]::ShowWindow($h, 7) | Out-Null   # minimize again without activating
"saved $Out ($($r.R)x$($r.B))"
