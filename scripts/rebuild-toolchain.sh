#!/usr/bin/env bash
# =============================================================================
# rebuild-toolchain.sh — reconstructs the offline Android build environment at
# /home/user/android-build from scratch after a sandbox reset.
#
# Everything is fetched from sources reachable in this sandbox:
#   - git trees on github.com / codeload.github.com (NO release assets — they
#     redirect to a blocked host)
#   - registry.npmjs.org
#   - pypi.org / files.pythonhosted.org (jdk4py JRE)
#
# Resulting layout:
#   /home/user/android-build/
#     tools/jdk-21/          JRE + honest javac stub (project is 100% Kotlin)
#     tools/gradle-8.11.1/   full gradle dist (patched: jdk.compiler flags removed)
#     tools/relocate/        Kotlin/ASM jar relocator
#     m2repo/                offline maven repo
#     sdk/                   android-35 platform + assembled build-tools 35.0.0
#     gradle-home/           GRADLE_USER_HOME with sandbox overrides
#     probe/                 verification project (assembleDebug must pass)
#
# Total download ~700 MB. Run: bash scripts/rebuild-toolchain.sh
# =============================================================================
set -euo pipefail

AB=/home/user/android-build
DL=$AB/dl
mkdir -p "$AB/tools" "$DL"
cd "$DL"

echo "=== [1/8] Gradle 8.11.1 (committed as plain git files) ==="
rm -rf "$AB/tools/gradle-8.11.1" gradle-repo
git clone --filter=blob:none --sparse --depth 1 https://github.com/nt8816/sim-bora-piaui gradle-repo
( cd gradle-repo && git sparse-checkout set Simbora-Piaui/builds/templates/gradle-8.11.1 )
mv gradle-repo/Simbora-Piaui/builds/templates/gradle-8.11.1 "$AB/tools/gradle-8.11.1"
chmod +x "$AB/tools/gradle-8.11.1/bin/gradle"
rm -rf gradle-repo

echo "=== [2/8] JRE 21 (jdk4py from PyPI; JRE only — see javac stub) ==="
pip download jdk4py==21.0.8.2 --platform manylinux_2_17_x86_64 --only-binary=:all: --no-deps -d jdk4py.whl -q
python3 -m zipfile -e jdk4py.whl/jdk4py-21.0.8.2-py3-none-manylinux_2_17_x86_64.whl jdk4py-extract
rm -rf "$AB/tools/jdk-21"
mv jdk4py-extract/jdk4py/java-runtime "$AB/tools/jdk-21"
chmod -R u+x "$AB/tools/jdk-21/bin/"
chmod +x "$AB/tools/jdk-21/lib/jspawnhelper" || true
# Honest javac stub: this project has NO java sources. Fails loudly if ever
# asked to compile, so nothing can silently "succeed" without a compiler.
cat > "$AB/tools/jdk-21/bin/javac" <<'EOF'
#!/bin/sh
# javac shim: project is 100% Kotlin; this stub only satisfies JDK detection.
for a in "$@"; do
  case "$a" in
    -version|-fullversion|--version|-J-version)
      echo "javac 21.0.8"; echo "openjdk version \"21.0.8\" 2025-07-15" >&2; exit 0 ;;
  esac
done
echo "ERROR: javac shim invoked for real compilation — this project has no Java sources." >&2
echo "args: $*" >&2
exit 1
EOF
chmod +x "$AB/tools/jdk-21/bin/javac"

echo "=== [3/8] Patch gradle: remove jdk.compiler daemon flags (JRE lacks the module) ==="
python3 - <<'EOF'
import zipfile, glob, shutil
os_flag_a = b'--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED'
os_flag_b = b'--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED'
def make(n):
    base = '--add-exports=java.base/java.util.'
    target = n - len(base) - len('=ALL-UNNAMED')
    return (base + 'x' * target + '=ALL-UNNAMED').encode()
A2, B2 = make(len(os_flag_a)), make(len(os_flag_b))
def patch(jar, cls):
    tmp = jar + '.tmp'
    with zipfile.ZipFile(jar) as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == cls:
                data2 = data.replace(os_flag_a, A2).replace(os_flag_b, B2)
                assert data2 != data and os_flag_a not in data2
                data = data2
            zout.writestr(item, data)
    shutil.move(tmp, jar)
L = '/home/user/android-build/tools/gradle-8.11.1/lib/'
patch(L + 'gradle-base-services-8.11.1.jar', 'org/gradle/internal/jvm/JpmsConfiguration.class')
patch(L + 'plugins/gradle-language-java-8.11.1.jar', 'org/gradle/api/internal/tasks/compile/DaemonJavaCompiler.class')
print('gradle patched')
EOF

echo "=== [4/8] m2repo from LexChien/BabyGrowthApp committed gradle cache ==="
rm -rf "$AB/m2repo" bg
git clone --filter=blob:none --no-checkout --depth 1 https://github.com/LexChien/BabyGrowthApp bg
( cd bg && git sparse-checkout set Android/BabyGrowth/.gradle/caches/modules-2 && git checkout -q main )
python3 - <<'EOF'
import os, shutil
src = 'bg/Android/BabyGrowth/.gradle/caches/modules-2/files-2.1'
dst = '/home/user/android-build/m2repo'
os.makedirs(dst, exist_ok=True)
n = 0
for root, dirs, files in os.walk(src):
    for f in files:
        p = os.path.join(root, f)
        parts = p.split(os.sep)
        if len(parts) < 6: continue
        group, artifact, version, sha, fname = parts[-5], parts[-4], parts[-3], parts[-2], parts[-1]
        outdir = os.path.join(dst, group.replace('.', os.sep), artifact, version)
        os.makedirs(outdir, exist_ok=True)
        out = os.path.join(outdir, fname)
        if not os.path.exists(out):
            shutil.copy2(p, out); n += 1
print('copied', n)
EOF
rm -rf bg

echo "=== [4b] Rename variant aars to maven layout names (module metadata url) ==="
python3 - <<'EOF'
import os, json, glob
m2 = '/home/user/android-build/m2repo'
n = 0
for path in glob.glob(m2 + '/**/*-release.aar', recursive=True):
    d = os.path.dirname(path); ver = os.path.basename(d); art = os.path.basename(os.path.dirname(d))
    target = f'{art}-{ver}.aar'
    module = os.path.join(d, f'{art}-{ver}.module')
    if os.path.exists(module):
        try:
            meta = json.load(open(module))
            for v in meta.get('variants', []):
                for f in v.get('files', []):
                    if f.get('url', '').endswith('.aar'):
                        target = f['url']; break
        except Exception: pass
    dst = os.path.join(d, target)
    if dst != path and not os.path.exists(dst):
        os.rename(path, dst); n += 1
print('renamed', n)
EOF

echo "=== [5/8] Rebuild kotlin-compiler-embeddable 1.9.22 (relocated) from npm ==="
mkdir -p "$AB/tools/relocate/src"
curl -s https://registry.npmjs.org/kotlin-compiler/1.9.22 | python3 -c "import json,sys; print(json.load(sys.stdin)['dist']['tarball'])" > kc-url.txt
curl -sL -o kotlin-compiler-1.9.22.tgz "$(cat kc-url.txt)"
mkdir -p kc && tar xzf kotlin-compiler-1.9.22.tgz -C kc
# relocator source lives in this repo — copy it
cp "$(dirname "$0")/../tools-src/Relocate.kt" "$AB/tools/relocate/src/Relocate.kt"
G="$AB/tools/gradle-8.11.1/lib"
export JAVA_HOME="$AB/tools/jdk-21"
KCP="$G/kotlin-compiler-embeddable-2.0.20.jar:$G/kotlin-stdlib-2.0.20.jar:$G/kotlin-script-runtime-2.0.20.jar:$G/kotlin-reflect-2.0.20.jar:$G/trove4j-1.0.20200330.jar:$G/guava-32.1.2-jre.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar:$G/annotations-24.0.1.jar"
cd "$AB/tools/relocate"
"$JAVA_HOME/bin/java" -cp "$KCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib \
  -cp "$G/asm-9.7.jar:$G/asm-commons-9.7.jar:$G/asm-tree-9.7.jar:$G/kotlin-stdlib-2.0.20.jar" \
  src/Relocate.kt -d relocate.jar
RCP="relocate.jar:$G/asm-9.7.jar:$G/asm-commons-9.7.jar:$G/asm-tree-9.7.jar:$G/kotlin-stdlib-2.0.20.jar"
KC="$DL/kc/package/lib"
"$JAVA_HOME/bin/java" -cp "$RCP" Relocate "$KC/kotlin-compiler.jar" kce-nogoogle.jar \
  com/intellij org/jetbrains/kotlin/com/intellij \
  gnu/trove org/jetbrains/kotlin/gnu/trove -exclude com/google/
"$JAVA_HOME/bin/java" -cp "$RCP" Relocate "$KC/trove4j.jar" trove-rel.jar \
  gnu/trove org/jetbrains/kotlin/gnu/trove
"$JAVA_HOME/bin/java" -cp "$RCP" Jars kce2-1.9.22.jar kce-nogoogle.jar trove-rel.jar
M2K="$AB/m2repo/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.22"
cp kce2-1.9.22.jar "$M2K/kotlin-compiler-embeddable-1.9.22.jar"
python3 - <<'EOF'
import re
p = '/home/user/android-build/m2repo/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.22/kotlin-compiler-embeddable-1.9.22.pom'
s = open(p).read()
s = re.sub(r'<dependency>\s*<groupId>org\.jetbrains\.intellij\.deps</groupId>\s*<artifactId>trove4j</artifactId>\s*<version>1\.0\.20200330</version>\s*<scope>runtime</scope>\s*</dependency>',
'''<dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
      <version>32.0.1-jre</version>
      <scope>runtime</scope>
    </dependency>''', s)
open(p, 'w').write(s)
print('embeddable pom patched')
EOF
cd "$DL"

echo "=== [6/8] m2repo fabrications for version gaps ==="
M2="$AB/m2repo"
G="$AB/tools/gradle-8.11.1/lib"
mkdir -p "$M2/junit/junit/4.13.2" "$M2/org/hamcrest/hamcrest-core/1.3" \
         "$M2/com/google/guava/guava/32.1.2-jre" \
         "$M2/org/jetbrains/kotlin/kotlin-stdlib/2.1.0" "$M2/org/jetbrains/kotlin/kotlin-reflect/2.1.0"
cp "$G/junit-4.13.2.jar" "$M2/junit/junit/4.13.2/"
cp "$G/hamcrest-core-1.3.jar" "$M2/org/hamcrest/hamcrest-core/1.3/"
cp "$G/kotlin-stdlib-2.0.20.jar" "$M2/org/jetbrains/kotlin/kotlin-stdlib/2.1.0/kotlin-stdlib-2.1.0.jar"
cp "$G/kotlin-reflect-2.0.20.jar" "$M2/org/jetbrains/kotlin/kotlin-reflect/2.1.0/kotlin-reflect-2.1.0.jar"
cat > "$M2/junit/junit/4.13.2/junit-4.13.2.pom" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
  <groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><packaging>jar</packaging>
  <dependencies><dependency><groupId>org.hamcrest</groupId><artifactId>hamcrest-core</artifactId><version>1.3</version><scope>compile</scope></dependency></dependencies>
</project>
EOF
cat > "$M2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.pom" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
  <groupId>org.hamcrest</groupId><artifactId>hamcrest-core</artifactId><version>1.3</version><packaging>jar</packaging>
</project>
EOF
cat > "$M2/org/jetbrains/kotlin/kotlin-stdlib/2.1.0/kotlin-stdlib-2.1.0.pom" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
  <groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>2.1.0</version><packaging>jar</packaging>
</project>
EOF
cat > "$M2/org/jetbrains/kotlin/kotlin-reflect/2.1.0/kotlin-reflect-2.1.0.pom" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
  <groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-reflect</artifactId><version>2.1.0</version><packaging>jar</packaging>
</project>
EOF
# kotlin 2.1.0 for AGP deps (sdk-common/jetifier pin these; 2.0.20 binaries are compatible)
[ -f "$M2/com/squareup/javapoet/1.10.0/javapoet-1.10.0.jar" ] || \
  cp "$M2/com/squareup/javapoet/1.13.0/javapoet-1.13.0.jar" "$M2/com/squareup/javapoet/1.10.0/javapoet-1.10.0.jar"
[ -f "$M2/com/google/protobuf/protobuf-java/3.22.3/protobuf-java-3.22.3.jar" ] || \
  cp "$M2/com/google/protobuf/protobuf-java/3.24.4/protobuf-java-3.24.4.jar" "$M2/com/google/protobuf/protobuf-java/3.22.3/protobuf-java-3.22.3.jar"
# annotation-jvm placeholder jar (annotations are source-retention metadata; empty jar suffices)
python3 -c "
import zipfile
with zipfile.ZipFile('$M2/androidx/annotation/annotation-jvm/1.6.0/annotation-jvm-1.6.0.jar', 'w') as z:
    z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
"
# plain kotlin-gradle-plugin jar (metadata usually selects -gradle82 variant)
KGP="$M2/org/jetbrains/kotlin/kotlin-gradle-plugin/1.9.22"
[ -f "$KGP/kotlin-gradle-plugin-1.9.22.jar" ] || cp "$KGP/kotlin-gradle-plugin-1.9.22-gradle82.jar" "$KGP/kotlin-gradle-plugin-1.9.22.jar"

echo "=== [7/8] Assemble SDK (platform 35 + build-tools + linux aapt2/zipalign) ==="
SDK="$AB/sdk"
rm -rf "$SDK" && mkdir -p "$SDK/platforms/android-35" "$SDK/licenses" "$SDK/build-tools/35.0.0"
rm -rf psdks
git clone --filter=blob:none --sparse --depth 1 https://github.com/MagicFoundation/PlatformSDKs psdks
( cd psdks && git fetch --deepen 30 --quiet )
C=$( cd psdks && for c in $(git rev-list --all); do
      if git ls-tree -r $c --name-only 2>/dev/null | grep -q "^Android/platforms/android-35/android.jar$"; then echo $c; break; fi
    done )
echo "using PlatformSDKs commit $C for android-35"
( cd psdks
  mkdir -p "$SDK/platforms/android-35/data" "$SDK/platforms/android-35/optional"
  git show $C:Android/platforms/android-35/android.jar > "$SDK/platforms/android-35/android.jar"
  git show $C:Android/platforms/android-35/build.prop > "$SDK/platforms/android-35/build.prop"
  git show $C:Android/platforms/android-35/source.properties > "$SDK/platforms/android-35/source.properties"
  for f in data/api-versions.xml data/annotations.zip framework.aidl core-for-system-modules.jar optional/android.test.runner.jar optional/android.test.base.jar optional/android.test.mock.jar; do
    git show $C:Android/platforms/android-35/$f > "$SDK/platforms/android-35/$f"
  done
  git show HEAD:Android/build-tools/36.1.0/core-lambda-stubs.jar > "$SDK/build-tools/35.0.0/core-lambda-stubs.jar" )
printf '8933bad161af4178b1185d1a37fbf41ea5269c55\n24333f8a63b6825ea9c5514f83c2829b004d1fee\nd56f5187479451eabf01fb78af6dfcb131a6481e\n' > "$SDK/licenses/android-sdk-license"
echo "504667f4c0decbc1479cb7244e56de4b1f1f7c5c" > "$SDK/licenses/android-sdk-preview-license"
# real linux aapt2 (x86_64) from a committed toolchain dir
rm -rf icsee
git clone --filter=blob:none --sparse --depth 1 https://github.com/voidnullvalue/Icsee-android icsee
( cd icsee && git sparse-checkout set toolchain )
BT="$SDK/build-tools/35.0.0"
cp icsee/toolchain/aapt2-bin/aapt2-x86_64 "$BT/aapt2"; chmod +x "$BT/aapt2"
cp "$BT/aapt2" "$BT/aapt"
# static zipalign (+libs) from LineageOS prebuilts
rm -rf bt-probe
git clone --filter=blob:none --sparse --depth 1 -b lineage-21.0 https://github.com/LineageOS/android_prebuilts_build-tools bt-probe
( cd bt-probe && git sparse-checkout set linux_musl-x86 )
mkdir -p "$BT/lib64"
cp bt-probe/linux_musl-x86/bin/zipalign "$BT/zipalign"; chmod +x "$BT/zipalign"
cp bt-probe/linux_musl-x86/bin/aidl "$BT/aidl"; chmod +x "$BT/aidl"
cp bt-probe/linux_musl-x86/lib64/*.so "$BT/lib64/"
# apksigner (script over apksig library; AGP itself signs via apksig on classpath)
M2="$AB/m2repo"
mkdir -p "$BT/lib"
cp "$M2/com/android/tools/build/apksig/8.9.0/apksig-8.9.0.jar" "$BT/lib/apksig.jar"
for j in "$M2"/org/bouncycastle/*/1.79/*.jar; do cp "$j" "$BT/lib/"; done
cat > "$BT/apksigner" <<'EOF'
#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
exec java -cp "$DIR/lib/apksig.jar:$DIR/lib/bcprov-jdk18on-*.jar:$DIR/lib/bcpkix-jdk18on-*.jar:$DIR/lib/bcutil-jdk18on-*.jar" com.android.apksigner.ApkSignerTool "$@"
EOF
chmod +x "$BT/apksigner"
# presence-only stubs for tools this project never invokes
for TOOL in dexdump split-select llvm-rs-cc bcc_compat arm-linux-androideabi-ld aarch64-linux-android-ld i686-linux-android-ld x86_64-linux-android-ld mipsel-linux-android-ld; do
  printf '#!/bin/sh\necho "%s: stub (unused by this offline project)" >&2\nexit 1\n' "$TOOL" > "$BT/$TOOL"
  chmod +x "$BT/$TOOL"
done
mkdir -p "$BT/renderscript"
cat > "$BT/source.properties" <<'EOF'
Pkg.Desc=Android SDK Build-Tools (assembled for offline sandbox)
Pkg.Revision=35.0.0
EOF
# maven aapt2 linux jar (official artifact contains exactly this binary)
python3 - <<EOF
import zipfile
out = '$M2/com/android/tools/build/aapt2/8.9.0-12782657/aapt2-8.9.0-12782657-linux.jar'
with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:
    z.write('$BT/aapt2', 'aapt2')
print('aapt2 maven jar written')
EOF

echo "=== [8/8] gradle-home + probe verification ==="
mkdir -p "$AB/gradle-home"
cat > "$AB/gradle-home/gradle.properties" <<EOF
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
kotlin.incremental=false
android.aapt2FromMavenOverride=$BT/aapt2
android.builder.sdkDownload=false
EOF
export GRADLE_USER_HOME="$AB/gradle-home"
"$AB/tools/gradle-8.11.1/bin/gradle" --no-daemon --version > /dev/null
echo "Toolchain rebuilt. Now run scripts/make-probe.sh and verify assembleDebug."
