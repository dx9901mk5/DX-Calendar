plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

// 若位於本機雲端同步路徑且存在 D:/Android，自動將建置快取導向非同步區避免鎖檔；一般環境維持標準預設
if (file("D:/Android").exists() && rootDir.absolutePath.contains("Google")) {
    allprojects {
        layout.buildDirectory.set(file("D:/Android/dx_calendar_build/${project.name}"))
    }
}
