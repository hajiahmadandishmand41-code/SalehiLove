#!/usr/bin/env bash
# Creates the toolchain verification project at /home/user/android-build/probe
set -euo pipefail
AB=/home/user/android-build
mkdir -p $AB/probe/app/src/main/java/com/example/probe $AB/probe/app/src/main/res/values
cd $AB/probe
cat > settings.gradle <<'EOF'
pluginManagement { repositories { maven { url '/home/user/android-build/m2repo' } } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { maven { url '/home/user/android-build/m2repo' } }
}
rootProject.name = 'probe'
include ':app'
EOF
cat > build.gradle <<'EOF'
plugins {
    id 'com.android.application' version '8.9.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
EOF
cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
kotlin.incremental=false
android.useAndroidX=true
EOF
cat > local.properties <<EOF
sdk.dir=$AB/sdk
EOF
cat > app/build.gradle <<'EOF'
plugins { id 'com.android.application'; id 'org.jetbrains.kotlin.android' }
android {
    namespace 'com.example.probe'
    compileSdk 35
    buildToolsVersion '35.0.0'
    defaultConfig { applicationId 'com.example.probe'; minSdk 26; targetSdk 35; versionCode 1; versionName '1.0' }
    buildFeatures { compose true }
    composeOptions { kotlinCompilerExtensionVersion '1.5.8' }
    compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = '17' }
}
dependencies {
    implementation platform('org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.3')
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation platform('androidx.compose:compose-bom:2024.02.02')
    implementation 'androidx.compose.ui:ui:1.6.3'
    implementation 'androidx.compose.material3:material3:1.2.1'
}
tasks.withType(JavaCompile).configureEach {
    options.fork = true
    options.forkOptions.executable = '/home/user/android-build/tools/jdk-21/bin/javac'
}
EOF
cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="پروب" android:supportsRtl="true" android:theme="@style/AppTheme">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF
cat > app/src/main/res/values/styles.xml <<'EOF'
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
EOF
cat > app/src/main/java/com/example/probe/MainActivity.kt <<'EOF'
package com.example.probe
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("سلام دنیا") }
    }
}
EOF
export JAVA_HOME=$AB/tools/jdk-21 GRADLE_USER_HOME=$AB/gradle-home
$AB/tools/gradle-8.11.1/bin/gradle --no-daemon --offline assembleDebug
echo "PROBE OK — APK at $AB/probe/app/build/outputs/apk/debug/app-debug.apk"
