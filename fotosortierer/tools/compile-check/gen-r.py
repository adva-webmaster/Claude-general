#!/usr/bin/env python3
"""Erzeugt aus strings.xml eine R-Klasse und eine BuildConfig für den Kompilier-Prüfstand."""
import os
import re

here = os.path.dirname(os.path.abspath(__file__))
xml = open(os.path.join(here, "../../app/src/main/res/values/strings.xml"), encoding="utf-8").read()
strings = re.findall(r'<string name="([a-z_0-9]+)"', xml)
plurals = re.findall(r'<plurals name="([a-z_0-9]+)"', xml)
target = os.path.join(here, "gen/de/ingvoss/fotosortierer")
os.makedirs(target, exist_ok=True)
out = "package de.ingvoss.fotosortierer\n\nobject R {\n    object string {\n"
for i, n in enumerate(strings):
    out += f"        const val {n} = {0x7f100000 + i}\n"
out += "    }\n    object plurals {\n"
for i, n in enumerate(plurals):
    out += f"        const val {n} = {0x7f110000 + i}\n"
out += '    }\n}\n\nobject BuildConfig {\n    const val VERSION_NAME = "1.0.0"\n    const val APPLICATION_ID = "de.ingvoss.fotosortierer"\n}\n'
open(os.path.join(target, "R.kt"), "w", encoding="utf-8").write(out)
print(f"{len(strings)} Strings, {len(plurals)} Plurals")
