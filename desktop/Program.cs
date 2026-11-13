using System;
using System.IO;
using System.Reflection;
using System.Runtime.Versioning;
using System.Windows;

[assembly: AssemblyTitle("MiGoo")]
[assembly: AssemblyDescription("Xiaomi power-button Google Wallet shortcut companion")]
[assembly: AssemblyCompany("DustinYe")]
[assembly: AssemblyProduct("MiGoo")]
[assembly: AssemblyVersion("2.1.3.0")]
[assembly: AssemblyFileVersion("2.1.3.0")]
[assembly: TargetFramework(".NETFramework,Version=v4.8",FrameworkDisplayName=".NET Framework 4.8")]

namespace PowerWallet {
    public static class Program {
        [STAThread] public static int Main(string[] args) {
            AppContext.SetSwitch("Switch.System.Windows.DoNotScaleForDpiChanges",false);
            bool ownsInstance=true;
            System.Threading.Mutex instance=null;
            if(!(args.Length>=2 && args[0]=="--self-test")) {
                instance=new System.Threading.Mutex(true,"Local\\MiGoo.Desktop.1",out ownsInstance);
                if(!ownsInstance){Native.FocusExisting();instance.Dispose();return 0;}
            }
            var app=new Application();
            if(args.Length>=2 && args[0]=="--self-test") {
                app.ShutdownMode=ShutdownMode.OnExplicitShutdown;
                try{SelfTests.Run(args[1]);return 0;}catch(Exception ex){Directory.CreateDirectory(args[1]);File.WriteAllText(Path.Combine(args[1],"failure.txt"),ex.ToString());return 1;}
            }
            app.DispatcherUnhandledException+=(s,e)=> {e.Handled=true;MessageBox.Show("程序遇到异常，请重新打开。\n"+e.Exception.Message,"MiGoo");};
            app.Run(new MainWindow(false));
            if(instance!=null){instance.ReleaseMutex();instance.Dispose();}return 0;
        }
    }
}

