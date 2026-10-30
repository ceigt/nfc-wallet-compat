# NFC Wallet Compat

**类原生 Android 安装国内钱包应用并实现 NFC 刷卡。**

本项目通过钱包兼容模块，帮助在类原生 Android 上安装的原版国内钱包使用手机内置 eSE 交通卡。当前已适配 **一加 Ace 3（PJE110）/ AlphaDroid 4.7 / Android 16**，使用原版一加／OPPO 钱包完成深圳地铁交通卡刷卡。

当前版本 **0.6.2**，包名 **`nfc.wallet.compat`**，使用 **libxposed API 102**。这是针对特定手机与系统组合的兼容项目，不是通用 NFC 修复包，也不是一加、OPPO、支付宝或交通卡发行方的官方项目。

**刷交通卡请将默认 NFC 付款应用设为“钱包”，并且只启用一个钱包兼容模块。**

## 下载

在 [Releases](https://github.com/ceigt/nfc-wallet-compat/releases) 下载 APK。源码编译方式见下文。

本项目不提供钱包、账号中心等厂商应用安装包，使用者需要自行准备适合设备的正版应用。

## 适用条件

| 项目 | 当前适配范围 |
| --- | --- |
| 手机 | 一加 Ace 3，型号 `PJE110` |
| 系统 | 已检查的 AlphaDroid 4.7 / Android 16 环境 |
| 框架 | 支持 **现代 libxposed API 102** 的 LSPosed；验证环境为 2.2.1（7912） |
| 钱包 | 原版 `com.finshell.wallet` 5.51.5（versionCode `551053`）；UI 兼容限定此版本 |
| 硬件 | NFC 和内置 eSE；模块限定终端 `eSE1` |

手机需要已具备正常工作的 Root、注入环境和兼容的 LSPosed。本项目不包含刷机或安装 Root 框架教程。

APK 的最低 Android 版本为 9，但 NFC 路由适配代码只在 `PJE110`、Android API 36 及以上尝试安装，且仍会校验系统接口。**这不代表其他机型、Android 版本或未来系统更新已经适配。**

## 安装与使用

1. 安装并准备好原版钱包及其需要的账号服务，自行完成账号登录。不要向他人提供密码或验证码。
2. 安装本项目 APK，打开 LSPosed，启用 **NFC Wallet Compat**。
3. 核对固定作用域，只有以下三项：

   | 应用 | 包名 |
   | --- | --- |
   | 钱包 | `com.finshell.wallet` |
   | NFC Service | `com.android.nfc` |
   | SecureElementApplication | `com.android.se` |

4. 停用旧 **Wallet Alpha Compat** 以及其他同时修改钱包交通卡路由的兼容模块。
5. **整机重启。** 本模块没有热重载功能，更新、停用或切换模块后也需要重启。
6. 打开系统 NFC，将默认钱包／默认 NFC 付款应用设为 **钱包**。
7. 进入钱包的“乘车”页面，按发行方流程开通或选择交通卡；充值和费用以钱包及发行方说明为准。
8. 使用手机 NFC 感应区域靠近闸机或读卡器，首次使用请现场验证。

模块没有独立的设置界面，安装后请在 LSPosed 管理启用状态。它不会替你登录、开卡、充值或完成付款。

## 官方保修与电池换新

0.6.1 增加原版钱包 5.51.5 的设备识别兼容，0.6.2 修复旧设备信息缓存导致的间歇识别失败。在已适配的一加 Ace 3 上，进入“官方保修 → 电池换新”，可以使用本机真实 IMEI 查询并显示机型。模块读取硬件 IMEI，不需要手填标识，也不在安装包中保存个人 IMEI。

这项功能需要钱包拥有设备标识读取权限。使用前可在电脑 ADB 中执行：

```powershell
adb shell "su -c 'cmd appops set com.finshell.wallet READ_DEVICE_IDENTIFIERS allow'"
```

该系统授权适用于钱包应用整体。模块的回传范围限定于 `rinsurance.finzfin.com` 的官方保修和电池产品两个页面，并检查钱包证书、UID、版本以及设备型号。模块保持设备信息回传的桥接统计和原生共享缓存中的原始脱敏值，不记录 IMEI。官方保修页自动识别失败时，用本机真实 IMEI 重试官网自身的查询；手动查询其他设备时保留用户输入。

0.6.2 会在确认当前官方页面完成加载后启用自动重查，不再依赖首次设备信息回调。若网页桥接缓存中的设备信息不属于本机，会按需刷新 `getDeviceInfo`、`getBaseDeviceInfo`、`getHeader` 三项内存及网页存储缓存。账号令牌等其他缓存项不在刷新范围内。这样也能处理钱包冷启动后仍残留全零设备信息的情况。自动查询中迟到的空设备／全零结果不会覆盖已经确认的本机信息；新的官方拒绝和手动查询其他设备仍按官网原逻辑处理。

授权后重新打开钱包。更新模块仍按安装步骤整机重启。若不需要该识别功能，可以恢复默认权限：

```powershell
adb shell "su -c 'cmd appops set com.finshell.wallet READ_DEVICE_IDENTIFIERS default'"
```

保修日期、购买资格、产品价格及维修权益由官方服务判定；模块不改变这些结果，也不代为购买。网页结构或钱包接口更新后，可能需要重新适配。

## 默认应用与支付宝碰一下

### 默认支付宝，还能刷钱包交通卡吗？

**当前版本应保持默认钱包。已在适配设备上确认：默认支付宝时无法过闸，切回钱包后恢复。**

在适配设备上切换默认应用进行路由对照时：

| 默认应用 | 交通卡四条专用 AID 路由 |
| --- | --- |
| 钱包 | 存在，指向 eSE1（`0xC0`，电源状态 `0x3B`） |
| 支付宝 | 从硬件路由表中消失 |
| 恢复钱包 | 四条路由恢复 |

2026-10-20 的实体闸门测试与路由检查一致：默认支付宝时，交通卡请求出现“无路由”，现场尝试未能过闸；切回钱包后，请求重新到达 eSE1 并成功过闸。本模块尚未将交通卡路由与默认付款应用完全分离。

### 默认钱包，会影响支付宝碰一下吗？

在已观察的设备上，默认钱包时，支付宝蓝色“碰一下”设备可以在**亮屏解锁后**完成付款。不要把这理解为所有终端和所有屏幕状态都已验证。

熄屏时曾出现完全无响应、解锁后付款成功的反馈。目前不能确定是默认应用、终端所用 NFC 通道、系统屏幕策略还是其他因素造成的；**不承诺支付宝熄屏兼容**。支付宝的[官方使用说明](https://render.alipay.com/p/c/180020570000133336/index.html)描述的常规条件也是亮屏解锁、开启 NFC。

交通卡与支付宝碰一下使用的 NFC 通道可能不同，交通卡可熄屏使用并不代表支付宝也可熄屏使用。

## 已验证与未验证

截至 2026-10-30：

- **0.6.2 稳定性修复：** 针对旧设备信息缓存、已完成加载但回调来源尚未更新的页面及迟到弹窗增加恢复处理；自动检查覆盖缓存失效、保留其他缓存、手动查询和服务器拒绝。连续冷启动与模拟旧缓存的实机结果随 Release 验证记录提供。
- **0.6.1 新增复核：** GitHub Actions 完成策略、拦截链和网页回退测试及 APK 构建；在适配设备上，官方保修和电池换新页面无需手动输入，显示机型与硬件 IMEI 一致。NFC／eSE 路由实现沿用 0.6.0；本次更新后的实体过闸仍需现场验证。

- **0.6.0 已检查：** 三个作用域实际通过 API 102 加载；钱包主界面和现有深圳通互联互通卡页面正常；显示“靠近读卡器”；四条交通卡专用路由正常；三次冷启动通过，检查期间未发现钱包、NFC 或安全芯片相关崩溃。
- **0.6.0 实体刷卡记录：** 默认钱包时，已完成四次深圳地铁成功过闸，均有使用者现场确认及对应的交通卡请求到达 eSE1 的日志；保留记录中未发现模块加载失败、相关崩溃或标准 NFC 控制器错误。
- **默认支付宝测试：** 现场尝试未能过闸，日志记录交通卡请求无路由；切回默认钱包后恢复成功过闸。
- **尚未验证：** 香港交通卡／八达通、所有锁屏／熄屏组合，以及其他手机和 ROM。

以上结果仅覆盖当前适配环境。其他设备或系统中的“靠近读卡器”提示和正确路由表，仍不能代替该环境的实体闸门验证。

## 更新、迁移与回退

- 从旧包 `io.github.knox.walletalphacompat.test` 迁移时，新包属于独立应用：安装新模块、停用旧模块、核对三个作用域，再重启。无需清除钱包或账号数据。
- 后续更新使用相同包名及相同签名、递增 versionCode 覆盖安装，再重启。
- 如果新版出现问题，在 LSPosed 停用新版，恢复原来可用版本的作用域，再重启。不要同时启用两版。
- 自行编译的 APK 使用自己的签名，不能直接覆盖其他签名的安装包。切换签名前先处理模块安装状态；无需卸载钱包或清除交通卡。

## 工作方式与边界

模块通过 `XposedModule.onPackageReady` 安装现代拦截链，使用 API 102 Hook ID，通过 `META-INF/xposed` 登记入口与固定作用域。不调用旧版 `de.robv.android.xposed` API。

主要兼容逻辑：

- 只忽略钱包缺失特定 Osense 后台提示方法导致的已知 `NoSuchMethodError`，保留其他异常。
- 在原钱包证书、UID、版本、默认付款应用和 eSE 能力检查通过时，兼容缺失的厂商 NFC 选择设置。
- 校验 NFC 接口、设备型号及路由归属后，将已知交通卡 AID 绑定到 eSE1；保留其他 AID，拒绝 SIM 路由冲突，绑定失败时尝试回滚。
- 在安全芯片服务原本因已知 APDU 权限问题拒绝访问时，仅对**证书和调用 UID 均匹配的原版钱包、eSE1 终端**提供兼容处理。它会改变该已校验调用的访问判定，请在理解这一点后使用。

未知接口不会猜测适配，可能需要后续针对性维护。交通卡、账户数据和厂商 APK 不随项目分发。

## 源码构建

当前提供 **Windows / PowerShell** 构建脚本，所需环境：

- JDK 17。
- Android SDK Platform 36（`platforms/android-36/android.jar`）。
- Android SDK Build Tools 36.0.0。
- Python 3，用于 APK 结构检查。
- Node.js，用于官网查询回退的隔离测试。

`lib/libxposed-api-102.0.0.jar` 是固定版本的编译依赖，构建时校验 `lib/api.sha256`；框架 API 类不会打入 APK。

示例路径可按本机环境修改：

```powershell
$sdk = 'C:\Android\Sdk'
$java = 'C:\Java\jdk-17\bin'

# 运行策略、接口、回滚和拦截链测试。
.\test.ps1 -AndroidSdk $sdk -JavaBin $java

# 使用自己的密钥。密钥与密码文件请保存在仓库外。
.\build.ps1 `
  -AndroidSdk $sdk `
  -JavaBin $java `
  -SigningKey 'C:\Signing\nfc-wallet-compat.p12' `
  -PasswordFile 'C:\Signing\store-password.txt' `
  -KeyAlias 'nfc-wallet-compat' `
  -OutputApk '.\nfc-wallet-compat-0.6.2.apk'

python .\verify-apk.py .\nfc-wallet-compat-0.6.2.apk
```

GitHub Actions 会在推送后运行检查并生成未签名 APK，产物包含源提交号及 SHA-256。下载构建产物后需要签名才能安装。构建任务不接触设备或个人 IMEI，发布签名私钥不上传 GitHub。

也可以使用 `build.ps1 -Unsigned -AndroidSdk $sdk -JavaBin $java -OutputApk '.\nfc-wallet-compat-unsigned.apk'` 构建未签名包。

`SigningKey` 应为已存在的签名密钥，`PasswordFile` 包含其密码；上述路径不是项目自带文件。发布签名私钥及密码不公开。用其他设备构建时不要期望生成与官方发布 APK 相同的签名字节。

源码目录：

```text
src/nfc/wallet/compat/       模块及兼容策略
tests/nfc/wallet/compat/     JVM 测试
META-INF/xposed/            API 102 入口、配置及作用域
res/                        Android 描述资源
lib/                        编译依赖、散列与第三方许可
build.ps1                   编译、打包、对齐与签名
test.ps1                    自动检查
verify-apk.py               APK 元数据与依赖边界检查
```

## 问题反馈

提交 Issue 时请提供手机型号、ROM 和 Android 版本、LSPosed 版本、钱包版本、模块版本、默认 NFC 应用，以及亮屏／锁屏／熄屏状态和复现时间。日志只保留相关片段，提交前删除账号、卡号、交易信息、令牌等个人信息。

## 许可证与第三方依赖

本项目使用 [Apache License 2.0](LICENSE)。libxposed API 102.0.0 同样使用 Apache-2.0，许可和发布元数据位于 `lib/`。

- [libxposed API 文档](https://jingmatrix.github.io/libxposed-api/io/github/libxposed/api/package-summary.html)
- [API 编译依赖来源](https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/)

钱包与账号应用、交通卡及相关商标属于各自权利人，不随本项目分发。
