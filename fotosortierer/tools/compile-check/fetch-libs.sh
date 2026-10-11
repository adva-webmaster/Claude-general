#!/bin/bash
# Lädt die Maven-Central-Jars, die der Prüfstand zusätzlich braucht (Android-Framework, Coil, osmdroid).
set -e
cd "$(dirname "$0")"
mkdir -p libs
B=https://repo1.maven.org/maven2
get() { for i in 1 2 3 4 5 6 7 8; do c=$(curl -s -L -o "$2" -w '%{http_code}' "$1"); [ "$c" = 200 ] && return 0; sleep $((i * 4)); done; echo "FEHLER $1 ($c)"; return 1; }
get $B/org/robolectric/android-all/17-robolectric-15733970/android-all-17-robolectric-15733970.jar libs/android-all.jar
aar() { get "$1" "libs/$2.aar"; unzip -o -q -j "libs/$2.aar" classes.jar -d "libs/t_$2"; mv "libs/t_$2/classes.jar" "libs/$2.jar"; rm -rf "libs/t_$2" "libs/$2.aar"; }
for m in coil-android coil-core-android coil-compose-android coil-compose-core-android; do aar $B/io/coil-kt/coil3/$m/3.6.3/$m-3.6.3.aar $m; done
aar $B/io/coil-kt/coil3/coil-video/3.6.3/coil-video-3.6.3.aar coil-video
aar $B/org/osmdroid/osmdroid-android/6.1.20/osmdroid-android-6.1.20.aar osmdroid
echo "fertig"
