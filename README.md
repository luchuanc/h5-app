# H5 App

## 发布平台调试包

仓库默认配置为 `publishing.defaults.json`，游戏与列表使用 `https://games.lucc.site:8888`，本地直接构建即可使用三款游戏。平台构建时生成不入库的根目录 `publishing.json` 和可选 `publishing-icon.png`，覆盖默认名称、版本、图标和游戏列表。配置样例：

```json
{"appName":"游戏中心","versionName":"1.0.2","versionCode":3,"defaultGameId":"xiangsu","gameUrl":"https://games.lucc.site:8888/xiangsu/","customGameUrl":false,"catalogUrl":"https://games.lucc.site:8888/api/catalog","games":[{"id":"xiangsu","name":"像素远征","url":"https://games.lucc.site:8888/xiangsu/","version":"latest"}]}
```

使用 JDK 17、SDK platform 36/build-tools 35.0.0，设置 `JAVA_HOME`、`ANDROID_HOME`，执行 `sh ./gradlew --no-daemon :app:assembleDebug`。不要提交本机 Java 路径。平台图标为正方形 PNG；留空使用内置图标。原有调试和业务 Bundle 仍可在选择器中打开。

调试包右上角「游戏」可切换 Bundle，支持刷新远端列表，游戏 ID 和地址持久保存；冷启动先用缓存，再刷新地址。覆盖安装时，配置中的 `previousCatalogUrls` 和上次内置列表地址会迁移到新版域名，包括缓存列表与已选游戏；不覆盖其他自定义列表或外部游戏链接。未升级的旧 APK 仍需保持旧列表入口可达或手动修改列表 URL。发布平台模式固定使用配置的 App 名称/图标，切换游戏不会改变桌面图标。

平台游戏打开时自动进入横屏全屏，支持左右横屏方向，并在返回游戏和冷启动时恢复；网页退出全屏也不会把游戏切回竖屏。方向变化由 Activity 处理，不重建 WebView，避免旋转时丢失游戏进度。原有业务/调试 Bundle 仍保留自己的全屏控制接口。
