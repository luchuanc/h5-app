# H5 App

## 启动与选择

首次启动显示原 Startup Poster，3 秒内连续点击海报 10 次才打开内容选择页，前 9 次不会打开。选择后立即进入并持久保存；后续冷启动和覆盖升级直接打开已有选择。没有常驻切换按钮，网页桥接、直接打开选择 Activity 和保存接口均拒绝再次选择。只有清除 App 数据后重新走海报与十次点击流程。

选择页保留远端列表刷新、自定义 H5 和业务 Bundle，隐藏海报自身及 Runtime Lab 调试项。容器不再注入 vConsole，不显示右上角「游戏」按钮；release 关闭 WebView 调试。游戏自身的页面和资源不被过滤或修改。

仓库默认配置为 `publishing.defaults.json`，列表地址为 `https://games.lucc.site:8888/api/catalog`，默认游戏为同域名的 `/zizou/`、`/xiangsu/`、`/backHome/`。`defaultGameId` 仅定义列表内容，不能代替用户首次选择。平台生成的不入库 `publishing.json` 和可选 `publishing-icon.png` 继续覆盖名称、图标、版本与列表。

冷启动使用缓存并后台刷新列表。`previousCatalogUrls` 与上次内置列表地址负责旧 IP 地址迁移，包括缓存列表与已选游戏；保留外部自定义列表和游戏。升级不会清除选择。未选择时使用平台配置的 App 名称和默认图标；选择后桌面显示所选内容对应的名称和图标。

App 内 HTTP/HTTPS 入口默认带 `topInset=host`，沿用网页已有的宿主适配模式，避免网页重复预留顶部状态栏高度；已有显式 `topInset` 参数保留，其他查询参数和锚点不变。原生容器负责系统状态栏避让，普通浏览器页面不受影响。

平台游戏自动横屏全屏，支持左右横屏；方向变化由 Activity 处理，不重建 WebView。业务 Bundle 保留其原生录音、外链和全屏接口。

## 构建与签名

需要 Node.js、JDK 17、Android SDK platform 36/build-tools 35.0.0，设置 `JAVA_HOME`、`ANDROID_HOME`。

- 调试与自动测试：`sh ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`
- 正式包：`node scripts/build-release.mjs`，产物 `app/build/outputs/apk/release/app-release.apk`。
- 平台项目构建命令设置为 `node scripts/build-release.mjs`，产物目录 `app/build/outputs/apk/release`。

正式构建优先读取环境变量 `H5_RELEASE_SIGNING_PROPERTIES` 指定的签名属性文件，其次使用本地 `keystore.properties`。两者均未配置时，在构建用户的 `~/.config/h5-app/signing/` 首次生成专用 RSA 3072 / PKCS12 正式身份，以后所有构建复用。目录权限 700、私钥和属性文件 600；密码随机生成、不传到命令行、不打印。已有私钥缺少配置时拒绝覆盖，请恢复原配置。该目录必须在服务器迁移前安全备份；不同构建主机默认生成不同身份，不能互相覆盖安装。

属性文件字段：`storeFile`（私钥绝对路径）、`storePassword`、`keyAlias`、`keyPassword`。不要提交属性文件、私钥或密码。直接运行 `assembleRelease` 而未配置签名可能生成未签名文件；发布必须使用上述正式构建入口并验证 APK。

正式签名与旧服务器调试签名不同：首次换签需要卸载旧 App，会丢失本地选择与游戏数据。此后同一正式签名且版本代码递增即可覆盖升级并保留数据。旧调试 APK 保留用于回滚；回滚跨签名同样需要卸载。版本降低时也不能作为普通更新安装。

## 桌面图标

每次构建根据 `games[].id/name/iconFile` 生成稳定的独立 launcher alias。`iconFile` 为 64 位小写十六进制文件名加 `.png`，文件由平台复制至 `publishing-icons/<iconFile>`。有专属 PNG 时打包使用，没有则回退 APK 默认图标；所有游戏仍可选择。图标、名称和 alias 映射只来自 APK 内生成资源，远端目录返回的 `icon` URL 不会直接变成桌面图标或组件名。修改平台图标需重新构建并安装 APK 才生效。

内置账本、积分使用原有图标。清除数据后重启恢复默认图标；冷启动和覆盖升级按持久选择同步，Android 13+ 批量切换 aliases，确保只有一个桌面启动入口。APK 替换广播也会同步，避免旧选中 alias 被移除时入口消失；新增但尚未随 APK 打包的游戏使用默认入口。
