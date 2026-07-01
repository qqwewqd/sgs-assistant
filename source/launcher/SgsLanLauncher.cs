using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Management;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

internal static class SgsLanLauncher
{
    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new LauncherForm());
    }
}

internal sealed class LauncherForm : Form
{
    private const int DefaultPort = 8080;

    private readonly string baseDir;
    private readonly string jarPath;
    private readonly Label statusLabel;
    private readonly Button startButton;
    private readonly Button stopButton;
    private readonly Button openButton;
    private readonly Button copyButton;
    private readonly CheckBox autoOpenCheckBox;
    private readonly ListBox addressList;
    private readonly TextBox logBox;

    private Process serverProcess;
    private bool stopping;
    private bool openedAutomatically;
    private bool startupFailed;

    public LauncherForm()
    {
        baseDir = AppDomain.CurrentDomain.BaseDirectory;
        jarPath = ResolveJarPath(baseDir);

        Text = "SGS 局域网版";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(760, 540);
        Size = new Size(820, 600);
        Font = new Font("Microsoft YaHei UI", 9F, FontStyle.Regular, GraphicsUnit.Point);

        TableLayoutPanel root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 5,
            Padding = new Padding(16),
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 56));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 122));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
        Controls.Add(root);

        Label titleLabel = new Label
        {
            AutoSize = false,
            Dock = DockStyle.Fill,
            Text = "SGS 局域网版",
            Font = new Font(Font.FontFamily, 18F, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleLeft,
        };
        root.Controls.Add(titleLabel, 0, 0);

        FlowLayoutPanel buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
        };
        root.Controls.Add(buttons, 0, 1);

        startButton = new Button { Text = "启动", Width = 96, Height = 34 };
        stopButton = new Button { Text = "停止", Width = 96, Height = 34, Enabled = false };
        openButton = new Button { Text = "打开本机页面", Width = 128, Height = 34, Enabled = false };
        copyButton = new Button { Text = "复制地址", Width = 96, Height = 34 };
        autoOpenCheckBox = new CheckBox { Text = "启动成功后自动打开页面", Width = 180, Height = 34, Checked = true };
        buttons.Controls.Add(startButton);
        buttons.Controls.Add(stopButton);
        buttons.Controls.Add(openButton);
        buttons.Controls.Add(copyButton);
        buttons.Controls.Add(autoOpenCheckBox);

        GroupBox addressGroup = new GroupBox
        {
            Dock = DockStyle.Fill,
            Text = "访问地址",
            Padding = new Padding(10),
        };
        root.Controls.Add(addressGroup, 0, 2);

        addressList = new ListBox
        {
            Dock = DockStyle.Fill,
            IntegralHeight = false,
        };
        addressGroup.Controls.Add(addressList);

        GroupBox logGroup = new GroupBox
        {
            Dock = DockStyle.Fill,
            Text = "提示信息",
            Padding = new Padding(10),
        };
        root.Controls.Add(logGroup, 0, 3);

        logBox = new TextBox
        {
            Dock = DockStyle.Fill,
            Multiline = true,
            ReadOnly = true,
            ScrollBars = ScrollBars.Vertical,
            WordWrap = false,
        };
        logGroup.Controls.Add(logBox);

        statusLabel = new Label
        {
            Dock = DockStyle.Fill,
            Text = "未启动",
            TextAlign = ContentAlignment.MiddleLeft,
        };
        root.Controls.Add(statusLabel, 0, 4);

        startButton.Click += delegate { StartServer(); };
        stopButton.Click += delegate { StopServer(); };
        openButton.Click += delegate { OpenSelectedOrLocalUrl(); };
        copyButton.Click += delegate { CopyAddresses(); };
        FormClosing += OnFormClosing;

        RefreshAddressList();
        AppendLog("准备就绪。点击“启动”后，本窗口会在后台运行服务，不再打开命令行窗口。");
        RefreshRuntimeState();
    }

    private static string ResolveJarPath(string baseDir)
    {
        string releaseJar = Path.Combine(baseDir, "assistant-0.0.1-SNAPSHOT.jar");
        if (File.Exists(releaseJar))
        {
            return releaseJar;
        }
        return Path.Combine(baseDir, "backend", "target", "assistant-0.0.1-SNAPSHOT.jar");
    }

    private void StartServer()
    {
        if (IsServerRunning())
        {
            AppendLog("服务已经在运行。");
            return;
        }

        if (!File.Exists(jarPath))
        {
            SetStatus("启动失败：未找到后端 jar");
            AppendLog("[错误] 未找到文件：" + jarPath);
            AppendLog("请重新打包局域网版。");
            return;
        }

        EnsureDirectories();
        RefreshAddressList();

        if (IsPortInUse(DefaultPort))
        {
            List<int> pids = FindSgsProcessIdsOnPort();
            SetExistingServiceState(pids.Count > 0 ? "已有服务在运行" : "端口已被占用");
            AppendLog("[提示] 端口 " + DefaultPort + " 已经有服务在运行，请不要重复启动。");
            AppendLog(pids.Count > 0
                    ? "可以直接使用现有服务；如需重启，请点击“停止”后再启动。"
                    : "8080 被其他程序占用，启动器不会停止非 SGS 进程。");
            return;
        }

        if (IsDatabaseLocked())
        {
            SetExistingServiceState("数据库正在被占用");
            AppendLog("[提示] 本地数据库 data\\sgs.mv.db 正在被另一个 SGS 服务占用。");
            AppendLog("如需重启，请点击“停止”后再启动。");
            return;
        }

        openedAutomatically = false;
        stopping = false;
        startupFailed = false;
        SetButtonsForRunning(true);
        openButton.Enabled = false;
        SetStatus("正在启动...");
        AppendLog("正在启动服务，请稍等。");

        try
        {
            ProcessStartInfo info = new ProcessStartInfo
            {
                FileName = "java",
                Arguments = "-jar \"" + jarPath + "\"",
                WorkingDirectory = baseDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8,
            };
            info.EnvironmentVariables["SGS_SERVER_PORT"] = DefaultPort.ToString();
            info.EnvironmentVariables["SGS_UPLOAD_DIR"] = "upload";
            info.EnvironmentVariables["SGS_BOOTSTRAP_ENABLED"] = "true";
            info.EnvironmentVariables["SGS_BOOTSTRAP_KEY"] = "123";
            info.EnvironmentVariables["SGS_JWT_SECRET"] = "sgs-lan-local-secret-changeable";
            AppendJavaUtf8Options(info);

            serverProcess = new Process { StartInfo = info, EnableRaisingEvents = true };
            serverProcess.OutputDataReceived += OnProcessOutput;
            serverProcess.ErrorDataReceived += OnProcessOutput;
            serverProcess.Exited += OnProcessExited;
            serverProcess.Start();
            serverProcess.BeginOutputReadLine();
            serverProcess.BeginErrorReadLine();
        }
        catch (Exception ex)
        {
            serverProcess = null;
            SetButtonsForRunning(false);
            SetStatus("启动失败");
            startupFailed = true;
            AppendLog("[错误] 启动失败：" + ex.Message);
            AppendLog("请确认电脑已安装 Java 17，并且 java 命令可以被系统找到。");
        }
    }

    private void StopServer()
    {
        List<int> pids = FindCurrentSgsProcessIds();
        foreach (int pid in FindSgsProcessIdsOnPort())
        {
            AddUnique(pids, pid);
        }
        if (IsServerRunning() && !pids.Contains(serverProcess.Id))
        {
            pids.Insert(0, serverProcess.Id);
        }

        if (pids.Count == 0)
        {
            SetButtonsForRunning(false);
            openButton.Enabled = IsPortInUse(DefaultPort);
            SetStatus(IsPortInUse(DefaultPort) ? "端口被其他程序占用" : "已停止");
            AppendLog(IsPortInUse(DefaultPort)
                    ? "[提示] 没有找到当前目录的 SGS 服务；8080 可能被其他程序占用。"
                    : "当前没有正在运行的 SGS 服务。");
            return;
        }

        stopping = true;
        SetStatus("正在停止...");
        AppendLog("正在停止服务，进程：" + string.Join(", ", pids));

        foreach (int pid in pids)
        {
            StopProcessTree(pid);
        }

        serverProcess = null;
        stopping = false;
        RefreshRuntimeState();
    }

    private void OnProcessOutput(object sender, DataReceivedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(e.Data))
        {
            return;
        }

        RunOnUiThread(() =>
        {
            AppendLog(e.Data);
            ParseServerLine(e.Data);
        });
    }

    private void OnProcessExited(object sender, EventArgs e)
    {
        RunOnUiThread(() =>
        {
            int exitCode = 0;
            try
            {
                exitCode = serverProcess == null ? 0 : serverProcess.ExitCode;
            }
            catch
            {
            }

            SetButtonsForRunning(false);
            SetStatus(startupFailed ? "启动失败" : stopping ? "已停止" : "服务已退出");
            AppendLog(stopping ? "服务已停止。" : "服务已退出，退出码：" + exitCode);
            serverProcess = null;
            stopping = false;
            startupFailed = false;
            RefreshRuntimeState();
        });
    }

    private void ParseServerLine(string line)
    {
        string url = ExtractUrl(line);
        if (!string.IsNullOrEmpty(url))
        {
            AddAddress(url);
        }

        if (line.IndexOf("Tomcat started", StringComparison.OrdinalIgnoreCase) >= 0
                || line.IndexOf("SGS LAN 已启动", StringComparison.OrdinalIgnoreCase) >= 0)
        {
            SetStatus("运行中");
            openButton.Enabled = true;

            if (autoOpenCheckBox.Checked && !openedAutomatically)
            {
                openedAutomatically = true;
                OpenUrl("http://localhost:" + DefaultPort + "/");
            }
        }

        if (line.IndexOf("APPLICATION FAILED TO START", StringComparison.OrdinalIgnoreCase) >= 0
                || line.IndexOf("Port " + DefaultPort + " was already in use", StringComparison.OrdinalIgnoreCase) >= 0)
        {
            startupFailed = true;
            SetStatus("启动失败");
            AppendLog("[提示] 如果端口被占用，可以关闭已有 SGS 服务后再启动。");
        }

        if (line.IndexOf("The file is locked", StringComparison.OrdinalIgnoreCase) >= 0
                || line.IndexOf("sgs.mv.db", StringComparison.OrdinalIgnoreCase) >= 0
                        && line.IndexOf("locked", StringComparison.OrdinalIgnoreCase) >= 0)
        {
            startupFailed = true;
            SetStatus("启动失败：数据库被占用");
            AppendLog("[提示] 已有 SGS 服务正在占用本地数据库，请不要重复启动。");
        }
    }

    private void RefreshAddressList()
    {
        addressList.Items.Clear();
        AddAddress("http://localhost:" + DefaultPort + "/");

        foreach (string address in GetLanAddresses())
        {
            AddAddress("http://" + address + ":" + DefaultPort + "/");
        }
    }

    private static string[] GetLanAddresses()
    {
        try
        {
            var result = new System.Collections.Generic.List<string>();
            foreach (NetworkInterface networkInterface in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (networkInterface.OperationalStatus != OperationalStatus.Up
                        || networkInterface.NetworkInterfaceType == NetworkInterfaceType.Loopback
                        || networkInterface.NetworkInterfaceType == NetworkInterfaceType.Tunnel)
                {
                    continue;
                }

                foreach (UnicastIPAddressInformation addressInfo in networkInterface.GetIPProperties().UnicastAddresses)
                {
                    IPAddress address = addressInfo.Address;
                    if (address.AddressFamily != AddressFamily.InterNetwork)
                    {
                        continue;
                    }

                    string value = address.ToString();
                    if (IsPrivateIpv4(value) && !result.Contains(value))
                    {
                        result.Add(value);
                    }
                }
            }
            result.Sort(StringComparer.Ordinal);
            return result.ToArray();
        }
        catch
        {
            return new string[0];
        }
    }

    private static bool IsPrivateIpv4(string address)
    {
        if (address.StartsWith("10.", StringComparison.Ordinal))
        {
            return true;
        }
        if (address.StartsWith("192.168.", StringComparison.Ordinal))
        {
            return true;
        }
        Match match = Regex.Match(address, @"^172\.(\d+)\.");
        if (!match.Success)
        {
            return false;
        }
        int second = int.Parse(match.Groups[1].Value);
        return second >= 16 && second <= 31;
    }

    private static string ExtractUrl(string line)
    {
        Match match = Regex.Match(line, @"https?://[^\s]+");
        if (!match.Success)
        {
            return string.Empty;
        }
        string url = match.Value.TrimEnd('.', ',', ';', '。');
        return url.EndsWith("/", StringComparison.Ordinal) ? url : url + "/";
    }

    private static bool IsPortInUse(int port)
    {
        TcpListener listener = null;
        try
        {
            listener = new TcpListener(IPAddress.Any, port);
            listener.Start();
            return false;
        }
        catch (SocketException)
        {
            return true;
        }
        finally
        {
            if (listener != null)
            {
                listener.Stop();
            }
        }
    }

    private void RefreshRuntimeState()
    {
        if (IsServerRunning())
        {
            SetButtonsForRunning(true);
            openButton.Enabled = true;
            SetStatus("运行中");
            return;
        }

        List<int> pids = FindCurrentSgsProcessIds();
        foreach (int pid in FindSgsProcessIdsOnPort())
        {
            AddUnique(pids, pid);
        }
        if (pids.Count > 0)
        {
            SetExistingServiceState("已有服务在运行");
            AppendLog("检测到已运行的 SGS 服务，进程：" + string.Join(", ", pids));
            return;
        }

        SetButtonsForRunning(false);
        openButton.Enabled = IsPortInUse(DefaultPort);
        SetStatus(IsPortInUse(DefaultPort) ? "端口已被占用" : "未启动");
    }

    private void SetExistingServiceState(string status)
    {
        startButton.Enabled = false;
        List<int> pids = FindCurrentSgsProcessIds();
        foreach (int pid in FindSgsProcessIdsOnPort())
        {
            AddUnique(pids, pid);
        }
        stopButton.Enabled = pids.Count > 0;
        openButton.Enabled = true;
        SetStatus(status);
    }

    private List<int> FindCurrentSgsProcessIds()
    {
        List<int> result = new List<int>();
        string normalizedBase = NormalizeForCompare(baseDir);
        string normalizedJar = NormalizeForCompare(jarPath);

        foreach (int pid in FindPortListeningProcessIds(DefaultPort))
        {
            string commandLine = GetProcessCommandLine(pid);
            string normalizedCommandLine = NormalizeForCompare(commandLine);
            if (normalizedCommandLine.Contains("assistant-0.0.1-snapshot.jar")
                    && (normalizedCommandLine.Contains(normalizedBase)
                            || normalizedCommandLine.Contains(normalizedJar)))
            {
                AddUnique(result, pid);
            }
        }

        foreach (ProcessInfo processInfo in FindJavaProcesses())
        {
            string normalizedCommandLine = NormalizeForCompare(processInfo.CommandLine);
            if (normalizedCommandLine.Contains("assistant-0.0.1-snapshot.jar")
                    && (normalizedCommandLine.Contains(normalizedBase)
                            || normalizedCommandLine.Contains(normalizedJar)))
            {
                AddUnique(result, processInfo.ProcessId);
            }
        }

        return result;
    }

    private List<int> FindSgsProcessIdsOnPort()
    {
        List<int> result = new List<int>();
        foreach (int pid in FindPortListeningProcessIds(DefaultPort))
        {
            string normalizedCommandLine = NormalizeForCompare(GetProcessCommandLine(pid));
            if (normalizedCommandLine.Contains("assistant-0.0.1-snapshot.jar"))
            {
                AddUnique(result, pid);
            }
        }
        return result;
    }

    private static List<int> FindPortListeningProcessIds(int port)
    {
        List<int> result = new List<int>();
        try
        {
            ProcessStartInfo info = new ProcessStartInfo
            {
                FileName = "netstat.exe",
                Arguments = "-ano -p tcp",
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.Default,
                StandardErrorEncoding = Encoding.Default,
            };
            using (Process process = Process.Start(info))
            {
                string output = process.StandardOutput.ReadToEnd();
                process.WaitForExit(5000);
                foreach (string rawLine in output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries))
                {
                    string line = rawLine.Trim();
                    if (line.IndexOf("LISTENING", StringComparison.OrdinalIgnoreCase) < 0)
                    {
                        continue;
                    }
                    string[] parts = Regex.Split(line, @"\s+");
                    if (parts.Length < 5 || !parts[1].EndsWith(":" + port, StringComparison.Ordinal))
                    {
                        continue;
                    }
                    int pid;
                    if (int.TryParse(parts[4], out pid))
                    {
                        AddUnique(result, pid);
                    }
                }
            }
        }
        catch
        {
        }
        return result;
    }

    private static List<ProcessInfo> FindJavaProcesses()
    {
        List<ProcessInfo> result = new List<ProcessInfo>();
        try
        {
            using (ManagementObjectSearcher searcher = new ManagementObjectSearcher(
                    "SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name='java.exe' OR Name='javaw.exe'"))
            using (ManagementObjectCollection collection = searcher.Get())
            {
                foreach (ManagementObject item in collection)
                {
                    result.Add(new ProcessInfo
                    {
                        ProcessId = Convert.ToInt32(item["ProcessId"]),
                        CommandLine = Convert.ToString(item["CommandLine"] ?? string.Empty),
                    });
                }
            }
        }
        catch
        {
        }
        return result;
    }

    private static string GetProcessCommandLine(int pid)
    {
        try
        {
            using (ManagementObjectSearcher searcher = new ManagementObjectSearcher(
                    "SELECT CommandLine FROM Win32_Process WHERE ProcessId=" + pid))
            using (ManagementObjectCollection collection = searcher.Get())
            {
                foreach (ManagementObject item in collection)
                {
                    return Convert.ToString(item["CommandLine"] ?? string.Empty);
                }
            }
        }
        catch
        {
        }
        return string.Empty;
    }

    private void StopProcessTree(int pid)
    {
        try
        {
            ProcessStartInfo info = new ProcessStartInfo
            {
                FileName = "taskkill.exe",
                Arguments = "/PID " + pid + " /T /F",
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.Default,
                StandardErrorEncoding = Encoding.Default,
            };
            using (Process process = Process.Start(info))
            {
                string output = process.StandardOutput.ReadToEnd();
                string error = process.StandardError.ReadToEnd();
                process.WaitForExit(8000);
                if (!string.IsNullOrWhiteSpace(output))
                {
                    AppendLog(output.Trim());
                }
                if (!string.IsNullOrWhiteSpace(error))
                {
                    AppendLog(error.Trim());
                }
            }
        }
        catch (Exception ex)
        {
            AppendLog("[错误] 停止进程 " + pid + " 失败：" + ex.Message);
        }
    }

    private static void AddUnique(List<int> values, int value)
    {
        if (!values.Contains(value))
        {
            values.Add(value);
        }
    }

    private static string NormalizeForCompare(string value)
    {
        return (value ?? string.Empty).Replace('\\', '/').Trim('"').ToLowerInvariant();
    }

    private bool IsDatabaseLocked()
    {
        string dbPath = Path.Combine(baseDir, "data", "sgs.mv.db");
        if (!File.Exists(dbPath))
        {
            return false;
        }

        try
        {
            using (new FileStream(dbPath, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
            {
                return false;
            }
        }
        catch (IOException)
        {
            return true;
        }
        catch (UnauthorizedAccessException)
        {
            return true;
        }
    }

    private static void AppendJavaUtf8Options(ProcessStartInfo info)
    {
        const string key = "JAVA_TOOL_OPTIONS";
        const string options = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8";
        string current = info.EnvironmentVariables[key];
        if (string.IsNullOrWhiteSpace(current))
        {
            info.EnvironmentVariables[key] = options;
            return;
        }

        if (current.IndexOf("-Dfile.encoding", StringComparison.OrdinalIgnoreCase) < 0)
        {
            info.EnvironmentVariables[key] = current + " " + options;
        }
    }

    private void AddAddress(string url)
    {
        for (int i = 0; i < addressList.Items.Count; i++)
        {
            if (string.Equals(addressList.Items[i].ToString(), url, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }
        }
        addressList.Items.Add(url);
        if (addressList.SelectedIndex < 0)
        {
            addressList.SelectedIndex = 0;
        }
    }

    private void OpenSelectedOrLocalUrl()
    {
        string url = addressList.SelectedItem == null
            ? "http://localhost:" + DefaultPort + "/"
            : addressList.SelectedItem.ToString();
        OpenUrl(url);
    }

    private void OpenUrl(string url)
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = url,
                UseShellExecute = true,
            });
        }
        catch (Exception ex)
        {
            AppendLog("[错误] 无法打开浏览器：" + ex.Message);
        }
    }

    private void CopyAddresses()
    {
        if (addressList.Items.Count == 0)
        {
            return;
        }

        StringBuilder builder = new StringBuilder();
        foreach (object item in addressList.Items)
        {
            builder.AppendLine(item.ToString());
        }
        try
        {
            Clipboard.SetText(builder.ToString());
            AppendLog("已复制访问地址。");
        }
        catch (Exception ex)
        {
            AppendLog("[错误] 复制失败：" + ex.Message);
        }
    }

    private void EnsureDirectories()
    {
        Directory.CreateDirectory(Path.Combine(baseDir, "data"));
        Directory.CreateDirectory(Path.Combine(baseDir, "upload"));
        Directory.CreateDirectory(Path.Combine(baseDir, "logs"));
    }

    private void AppendLog(string message)
    {
        logBox.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + message + Environment.NewLine);
    }

    private void SetStatus(string status)
    {
        statusLabel.Text = "状态：" + status;
    }

    private void SetButtonsForRunning(bool running)
    {
        startButton.Enabled = !running;
        stopButton.Enabled = running;
        openButton.Enabled = running;
    }

    private bool IsServerRunning()
    {
        return serverProcess != null && !serverProcess.HasExited;
    }

    private void RunOnUiThread(Action action)
    {
        if (IsDisposed || !IsHandleCreated)
        {
            return;
        }

        try
        {
            BeginInvoke(action);
        }
        catch
        {
        }
    }

    private void OnFormClosing(object sender, FormClosingEventArgs e)
    {
        if (!IsServerRunning())
        {
            if (FindCurrentSgsProcessIds().Count == 0)
            {
                return;
            }
        }

        DialogResult result = MessageBox.Show(
            "服务仍在运行。关闭窗口会停止 SGS 局域网服务，确定要关闭吗？",
            "SGS 局域网版",
            MessageBoxButtons.YesNo,
            MessageBoxIcon.Question);

        if (result != DialogResult.Yes)
        {
            e.Cancel = true;
            return;
        }

        stopping = true;
        StopServer();
    }

    private sealed class ProcessInfo
    {
        public int ProcessId { get; set; }
        public string CommandLine { get; set; }
    }
}
