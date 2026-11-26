# 保留 JS Bridge 方法（WebView addJavascriptInterface 通过反射调用）
-keepclassmembers class com.ghhub.app.webview.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *JavascriptInterface*
# Gson 数据类
-keep class com.ghhub.app.**.model.** { *; }
-keep class com.ghhub.app.**.entity.** { *; }
