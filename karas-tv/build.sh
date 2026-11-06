#!/bin/bash
# Manual build for TV Launcher — no Gradle needed.
#
# Needs: JDK 17+, an Android SDK (platform android-34 + build-tools 34.x),
# the Kotlin compiler (kotlinc 2.x) and a debug keystore. Override any path
# with an environment variable, e.g.
#   SDK=~/Android/Sdk KOTLINC=~/kotlinc ./build.sh
#
# Writes karas-tv-debug.apk in this directory.
set -e
cd "$(dirname "$0")"

SDK="${SDK:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
BT="${BT:-$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)}"
PLATFORM="${PLATFORM:-$SDK/platforms/android-34/android.jar}"
AAPT2="${AAPT2:-$BT/aapt2}"
D8="${D8:-$BT/d8}"
ZIPALIGN="${ZIPALIGN:-$BT/zipalign}"
APKSIGNER="${APKSIGNER:-$BT/apksigner}"
KOTLINC="${KOTLINC:-$HOME/kotlinc}"
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
PKG=com.moomoo.tvlauncherbase
VCODE=18
VNAME=2.8.1

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

rm -rf out
mkdir -p out/compiled-res out/gen out/classes out/dex

# Gradle supplies the package name from build.gradle; the manual pipeline injects it.
sed "s#<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">#<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PKG\">#" \
  app/src/main/AndroidManifest.xml > out/AndroidManifest.xml

echo "== aapt2 =="
"$AAPT2" compile --dir app/src/main/res -o out/compiled-res/
"$AAPT2" link -o out/base.apk --manifest out/AndroidManifest.xml -I "$PLATFORM" \
  --java out/gen --min-sdk-version 26 --target-sdk-version 34 \
  --version-code $VCODE --version-name $VNAME out/compiled-res/*.flat

echo "== kotlinc =="
java -Xmx2g -cp "$KOTLINC/lib/kotlin-compiler.jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -cp "$PLATFORM:$KOTLINC/lib/kotlin-stdlib.jar" -jvm-target 1.8 \
  -d out/classes $(find app/src/main/java -name '*.kt') $(find out/gen -name '*.java')

echo "== d8 (app + Kotlin stdlib — the stdlib MUST be dexed in, or the app crashes on launch) =="
(cd out/classes && jar cf ../app.jar .)
"$D8" --release --min-api 26 --lib "$PLATFORM" --output out/dex out/app.jar "$KOTLINC/lib/kotlin-stdlib.jar"

echo "== package + align + sign =="
cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q ../unsigned.apk classes*.dex)
"$ZIPALIGN" -f 4 out/unsigned.apk out/aligned.apk
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out karas-tv-debug.apk out/aligned.apk
"$APKSIGNER" verify karas-tv-debug.apk && ls -la karas-tv-debug.apk
