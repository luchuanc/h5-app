# H5 App

## 发布平台调试包

平台构建时生成不入库的根目录 `publishing.json` 和可选 `publishing-icon.png`。Gradle 将名称、版本、图标和游戏列表写入 APK。配置样例：

```json
{"appName":"游戏中心","versionName":"1.0.0","versionCode":1,"defaultGameId":"xiangsu","gameUrl":"http://服务器:8200/xiangsu/","customGameUrl":false,"catalogUrl":"http://服务器:8200/api/catalog","games":[{"id":"xiangsu","name":"像素远征","url":"http://服务器:8200/xiangsu/","version":"latest"}]}
```

使用 JDK 17、SDK platform 36/build-tools 35.0.0，设置 `JAVA_HOME`、`ANDROID_HOME`，执行 `sh ./gradlew --no-daemon :app:assembleDebug`。不要提交本机 Java 路径。平台图标为正方形 PNG；留空使用内置图标。原有独立构建仍可用，未配置发布平台时保留原 Bundle。

调试包右上角「游戏」可切换 Bundle，支持刷新远端列表，游戏 ID 和地址持久保存；冷启动先用缓存，再刷新地址。更新域名需保持旧列表入口可达或在选择器里修改列表 URL。发布平台模式固定使用配置的 App 名称/图标，切换游戏不会改变桌面图标。
