# SGS 面杀助手局域网版

SGS 面杀助手用于线下面杀：一台 Windows 电脑作为主机启动服务，其他玩家手机连接同一个 WiFi 或电脑热点，通过启动器显示的局域网地址进入游戏。

## 目录说明

- `source/`：GitHub 源码目录。
- `releases/SGS-LAN.zip`：给普通用户下载的 Windows 发布包。
- `releases/SGS-LAN/`：发布包解压后的内容预览。

武将图片不包含在源码和发布包中，用户启动后可在后台自行上传。

发布包默认不内置任何武将数据。首次启动只会创建空数据库和基础表结构，管理员需要在后台自行新增武将、上传图片。

## 普通用户使用

1. 下载并解压 `SGS-LAN.zip`。
2. 双击 `SGS-LAN.exe`。
3. 点击“启动”。
4. 电脑访问 `http://localhost:8080/`，手机访问启动器里显示的局域网地址。

要求：

- Windows
- Java 17+

首次进入页面时，用初始化入口创建管理员。

默认初始化密钥：

```text
123
```

## 源码构建

进入 `source/` 后执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-lan.ps1
```

构建要求：

- Java 17+
- Maven
- Node.js 18+
- Windows 自带或 .NET Framework 提供的 `csc.exe`

构建完成后，`source/SGS-LAN.exe` 会调用 `source/backend/target/assistant-0.0.1-SNAPSHOT.jar` 启动局域网服务。

## 运行数据

运行时会自动生成：

- `data/`：本地 H2 数据库。
- `upload/`：用户上传的图片。
- `logs/`：运行日志或临时日志。
