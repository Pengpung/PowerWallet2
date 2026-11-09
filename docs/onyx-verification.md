# 红米 Turbo 4 Pro（onyx）适配验证记录

日期：2026-11-09。桌面端 PowerWallet **2.0.0**（本 fork）；手机插件 **2.0.0**（本 fork，versionCode 6，applicationId `cn.peng.powerwallet`）。

## 设备

| 项目 | 值 |
| --- | --- |
| 机型 | REDMI Turbo 4 Pro（`25053RT47C`） |
| 设备代号 | `onyx` |
| 系统 | HyperOS `OS3.0.306.0.WOLCNXM` |
| Android | 16 / SDK 36 |
| 电源键设备 | `/dev/input/event2`（`pmic_pwrkey`） |
| Google 钱包 | `com.google.android.apps.walletnfcrel` 26.43.1009867093 |
| Shizuku | 13.6.0（server PID 28619） |

原项目已验证小米 13 Ultra；本 fork 验证的是红米 Turbo 4 Pro。两者均进入“已验证”路径。

## 只读探针

手机端助手 `--probe` 不修改任何设置，输出：

```text
PROBE uid=2000 device=onyx build=OS3.0.306.0.WOLCNXM android=16 sdk=36 verified=true hyperOs=true
PROBE input=/dev/input/event2 setting=mi_pay
PROBE getevent_alive=true
BRIDGE_READY
PROBE keyguard showing=true deviceLocked=true interactive=...
PROBE OK
```

结论：ADB shell 身份、电源键设备选择、`getevent`、隐藏 API 桥接和锁屏状态读取均正常。

## 桌面端自检

- `PowerWallet-Setup-2.0.0.exe --self-test`：**449 项通过**，38 组主题/分辨率/缩放布局。
- 覆盖：兼容性、onyx 已验证路径、旧签名自动卸载、旧包名迁移、恢复流程、安装顺序、日志脱敏和布局。
- 新增 2 项旧包迁移自检：`cn.haiyu.powerwallet` 先恢复/停止再卸载，之后才安装新包。
- 原始输出：`docs/onyx-self-test-results.txt`。

## 真机安装与激活

- 2.0.0 使用新包名 `cn.peng.powerwallet`，versionCode 6 / versionName 2.0.0。
- 旧包 `cn.haiyu.powerwallet` 先通过旧助手 `--restore` 恢复 `mi_pay`，旧助手退出后卸载；再安装 2.0.0。
- 桌面端/Shizuku 安装后 `double_click_power_key=google_pay`。
- 原值 `mi_pay` 已保存在 `/data/local/tmp/powerwallet-state.properties`。
- 助手状态：`已激活 · 数据线可拔出`。
- 桌面端内置 APK、手机端 `/data/local/tmp/powerwallet-helper.jar` 和安装的 APK 哈希一致（最终版 `322424818c62f50e8933920efcc61d91e560cfb8796499824dc54b1cb7d335f9`）。

## 真机双击与锁屏

- 锁屏双击：轻触震动，停留在锁屏，不闪现 Wallet，不额外弹出第二次验证界面。
- 指纹/人脸/密码验证完成后，Wallet 自动进入前台。
- 解锁状态双击：直接打开 Wallet。
- Wallet 启动使用 `cmd activity start-activity`，并加 `--activity-no-animation`；解锁轮询 50ms，双击判定窗口 80ms。用户确认速度「够快了」。
- 双击震动固定为 `VibrationEffect.EFFECT_TICK`（系统轻触），由用户在真机逐个试听后选定。
- 2.0.0 新包名版本再次由用户实测：解锁双击、锁屏双击均正常。

## Shizuku 免电脑激活（2.0.0）

### 实现

- 手机端新增 `ShizukuBridge`，使用 Shizuku API 13.1.5 的 `IShizukuService.newProcess`，以 Shizuku 的 shell 身份执行命令。
- 激活时把应用自身 `sourceDir` 的 APK 通过 stdin 流式写入 `/data/local/tmp/powerwallet-helper.jar`，再用 `setsid nohup app_process ... cn.peng.powerwallet.Helper` 启动助手。
- 状态刷新、停用恢复和重新激活分别执行 `--status`、`--restore`、重新部署启动。
- `AndroidManifest.xml` 声明 `moe.shizuku.manager.permission.API_V23`、`moe.shizuku.client.V3_SUPPORT` 和 `rikka.shizuku.ShizukuProvider`（authority `cn.peng.powerwallet.shizuku`）。
- 首次授权使用 `Shizuku.requestPermission(100)`，并通过 `OnRequestPermissionResultListener` 在授权成功后自动继续激活。

### 真机结果

- 首次点击「用 Shizuku 激活」弹出 Shizuku 授权弹窗，选择「始终允许」后自动完成激活。
- `double_click_power_key=google_pay`，新助手 `READY uid=10321 input=/dev/input/event2 original=mi_pay`，`BRIDGE_READY`。
- 杀死助手进程模拟系统清理：界面刷新为「未激活」；点击「用 Shizuku 重新激活」后成功启动新助手，无需电脑。
- **助手死亡后的恢复**：再次杀死助手，点击「停用并恢复」→「恢复」，Shizuku 执行 `--restore`，`settings get system double_click_power_key` 从 `google_pay` 变为 `mi_pay`。这解决了普通应用无法写系统设置的问题。
- 覆盖安装同签名新版本后，Shizuku 授权保留（界面显示「已连接并授权」），可直接重新激活。
- 关闭开发者选项期间，Shizuku 服务与 `setsid` 助手仍存活；用户此前已确认双击可用。该结果不是无条件保证。

## 自动恢复 mi_pay 的限制

- 普通应用身份仍无法写 `double_click_power_key`：HyperOS SettingsProvider 拒绝 `WRITE_SETTINGS` / `WRITE_SECURE_SETTINGS`，异常 `IllegalArgumentException: You cannot keep your settings in the secure settings.`。
- 2.0.0 不再依赖应用自身权限：助手已停止时，由 Shizuku 的 shell 身份执行 `--restore`，读取备份并恢复 `mi_pay`。该路径已真机验证。
- 如果 Shizuku 未运行且助手已停止，手机端无法自动恢复；需启动 Shizuku 或连接电脑。

## 手机端界面

- 卡片式界面与官方桌面版风格一致；右上角为「系统/深色/浅色」分段控件。
- 新增 Shizuku 激活卡片，显示「未运行 / 等待授权 / 已连接并授权」和激活按钮。
- 切换使用 `UiTheme` 调色板和原地 `applyTheme()`，不调用 `recreate()`；真机切换前后 Activity 任务 ID 不变，用户确认「不闪了」。
- 2.0.0 修复了 Shizuku 卡片标题在浅色主题下未换色的问题；浅色/深色截图均已复核。
- 浅色/深色截图：`docs/PowerWallet-phone-light.png`、`docs/PowerWallet-phone-dark.png`。
- 矢量电源图标 `android/res/drawable/ic_power.xml`，不再受字体缺字方框影响。

## 未测试 / 限制

- 未测试真实 NFC 读卡器付款。
- 未实际重启手机后从 Shizuku 激活（按设计应可用，但本次未做整机重启测试）。
- 未逐一验证其他 HyperOS 机型；通用模式接受 `ro.build.version.incremental` 以 `OS` 开头的构建，桌面端同时参考 `ro.mi.os.version.name`。
- HyperOS 大版本更新后可能需要重新核对双击电源键实现。
- 关闭开发者选项后的存活依赖系统是否清理进程，不是稳定保证；失效后可用 Shizuku 重新激活。

## 关于原项目验证

原项目的 `docs/verification.md` 记录的是小米 13 Ultra（ishtar，`OS3.0.308.0.WMACNXM`）的验证；本文件记录的是本 fork 在 onyx 上的验证，两者不互相替代。
