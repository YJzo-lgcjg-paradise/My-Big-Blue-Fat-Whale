using System.Runtime.InteropServices;

namespace MbfwHost;

internal static class Program
{
    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SetCurrentProcessExplicitAppUserModelID(string appId);

    [STAThread]
    private static void Main()
    {
        // 让任务栏把窗口当成独立应用，用自己的图标
        try { SetCurrentProcessExplicitAppUserModelID("Dayufish.MyBigBlueFatWhale"); } catch { }
        ApplicationConfiguration.Initialize();
        Application.Run(new HostForm());
    }
}
