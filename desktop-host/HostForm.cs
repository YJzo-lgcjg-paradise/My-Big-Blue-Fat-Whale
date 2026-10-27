using System.Diagnostics;
using System.Drawing;
using System.Net.Http;
using System.Net.Sockets;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace MbfwHost;

/// <summary>
/// 桌面外壳：一个 WinForms 窗口，里面嵌 Edge WebView2 内核。
/// 启动时先拉起 Java 后端（server\runtime + server\app），等端口通了再把页面装进窗口。
/// </summary>
public sealed class HostForm : Form
{
    private readonly string _baseDir = AppContext.BaseDirectory;
    private readonly Label _status;
    private readonly WebView2 _web = new() { Dock = DockStyle.Fill };
    private Process? _java;
    private int _port;
    private bool _closing;

    public HostForm()
    {
        Text = "My Big Blue Fat Whale · 记忆";
        StartPosition = FormStartPosition.CenterScreen;
        WindowState = FormWindowState.Maximized;
        MinimumSize = new Size(900, 600);
        MinimumSize = new Size(960, 640);
        BackColor = Color.FromArgb(8, 8, 8);

        try
        {
            var embedded = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            var icoFile = Path.Combine(_baseDir, "app.ico");
            Icon = embedded ?? (File.Exists(icoFile) ? new Icon(icoFile) : null);
        }
        catch
        {
            try
            {
                var icoFile = Path.Combine(_baseDir, "app.ico");
                if (File.Exists(icoFile))
                {
                    Icon = new Icon(icoFile);
                }
            }
            catch
            {
                // 取不到就用默认
            }
        }

        _status = new Label
        {
            Text = "正在启动记忆服务…",
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleCenter,
            ForeColor = Color.FromArgb(198, 167, 106),
            Font = new Font("Microsoft YaHei UI", 12F),
        };

        Controls.Add(_status);
        Shown += async (_, _) => await BootAsync();
        KeyPreview = true;
        KeyDown += (_, e) =>
        {
            if (e.KeyCode == Keys.F5 && _web.CoreWebView2 != null)
            {
                _web.CoreWebView2.Reload();
                e.Handled = true;
            }
        };
        FormClosing += OnClosing;
    }

    private async Task BootAsync()
    {
        try
        {
            _port = FindFreePort();
            StartServer(_port);
            var ready = await WaitForServerAsync(_port, TimeSpan.FromSeconds(90));
            if (!ready)
            {
                Fail("记忆服务没能起来。\n\n多半是端口被占用，或 server 目录不完整。");
                return;
            }
            await ShowWebViewAsync($"http://127.0.0.1:{_port}/?v=" + DateTime.Now.Ticks);
        }
        catch (Exception ex)
        {
            Fail("启动失败：" + ex.Message);
        }
    }

    private static int FindFreePort()
    {
        for (var p = 8790; p < 8820; p++)
        {
            try
            {
                var l = new TcpListener(System.Net.IPAddress.Loopback, p);
                l.Start();
                l.Stop();
                return p;
            }
            catch
            {
                // 被占用就下一个
            }
        }
        return 8790;
    }

    private void StartServer(int port)
    {
        var serverDir = Path.Combine(_baseDir, "server");
        var java = Path.Combine(serverDir, "runtime", "bin", "javaw.exe");
        var appDir = Path.Combine(serverDir, "app");
        var dataDir = Path.Combine(serverDir, "data");
        if (!File.Exists(java))
        {
            throw new FileNotFoundException("找不到 server\\runtime\\bin\\javaw.exe");
        }
        Directory.CreateDirectory(dataDir);

        var psi = new ProcessStartInfo
        {
            FileName = java,
            WorkingDirectory = serverDir,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        psi.ArgumentList.Add("-Dfile.encoding=UTF-8");
        psi.ArgumentList.Add("-Duser.language=zh");
        psi.ArgumentList.Add("-Duser.country=CN");
        psi.ArgumentList.Add("-Ddayufish.no-env-scan=true");
        psi.ArgumentList.Add("-Ddayufish.data.dir=" + dataDir);
        psi.ArgumentList.Add("-Dserver.port=" + port);
        psi.ArgumentList.Add("-cp");
        psi.ArgumentList.Add(appDir + Path.DirectorySeparatorChar + "*");
        psi.ArgumentList.Add("org.springframework.boot.loader.launch.JarLauncher");

        _java = Process.Start(psi) ?? throw new InvalidOperationException("无法启动 Java 进程");
    }

    private static async Task<bool> WaitForServerAsync(int port, TimeSpan timeout)
    {
        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(3) };
        var deadline = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < deadline)
        {
            try
            {
                var res = await http.GetAsync($"http://127.0.0.1:{port}/api/health");
                if (res.IsSuccessStatusCode)
                {
                    return true;
                }
            }
            catch
            {
                // 还没起来
            }
            await Task.Delay(500);
        }
        return false;
    }

    private async Task ShowWebViewAsync(string url)
    {
        _status.Text = "正在装载界面…";
        var userData = Path.Combine(_baseDir, "webview-data");
        // 清掉上一次的网页缓存，避免窗口里还是旧界面（localStorage 不动）
        try
        {
            var cache = Path.Combine(userData, "EBWebView", "Default", "Cache");
            if (Directory.Exists(cache)) Directory.Delete(cache, true);
        }
        catch
        {
            // 删不掉就算了
        }
        var env = await CoreWebView2Environment.CreateAsync(null, userData);
        await _web.EnsureCoreWebView2Async(env);
        _web.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
        _web.CoreWebView2.Settings.IsStatusBarEnabled = false;
        _web.CoreWebView2.Settings.AreDevToolsEnabled = true;
        _web.CoreWebView2.Profile.PreferredColorScheme = CoreWebView2PreferredColorScheme.Dark;
        _web.ZoomFactor = 1.0;

        Controls.Clear();
        Controls.Add(_web);
        _web.CoreWebView2.Navigate(url);
    }

    private void Fail(string message)
    {
        _status.Text = message;
        _status.ForeColor = Color.FromArgb(220, 120, 110);
    }

    private void OnClosing(object? sender, FormClosingEventArgs e)
    {
        if (_closing)
        {
            return;
        }
        _closing = true;
        try
        {
            if (_java is { HasExited: false })
            {
                _java.Kill(entireProcessTree: true);
                _java.WaitForExit(3000);
            }
        }
        catch
        {
            // 关不掉就算了
        }
    }
}
