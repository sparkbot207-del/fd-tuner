#!/usr/bin/env python3
"""
hpp_to_paramdefs.py — generate GeneratedParamDefs.kt from jackhumbert fardriver.hpp.

Parses every struct field, simulates the exact C bitfield/byte packing of the 12-byte
data block, and computes the precise word address + lo/hi byte + bit mask/shift for each
field. Validated against hand-verified fields (RatedVoltage@0x17, brake@0xE3 bit15, etc).

Addresses are reliable. Scales come from (1) a curated override table for the values we
know, then (2) best-effort parse of HPP comments. Sections are by block + name override;
refine the handful of cross-block fields by hand.

Usage: python hpp_to_paramdefs.py fardriver.hpp > GeneratedParamDefs.kt

Get fardriver.hpp from:
  https://raw.githubusercontent.com/jackhumbert/fardriver-controllers/main/fardriver.hpp
"""
import re, sys

HPP = sys.argv[1] if len(sys.argv) > 1 else "fardriver.hpp"

# ---- curated scales we are confident about (display = raw / scale) ----
SCALE = {
    "RatedVoltage": 10.0, "LowVolProtect": 10.0, "HighVolProtect": 10.0,
    "HighVolRestore": 10.0, "PhaseOffset": 10.0, "ThrottleVoltage": 100.0,
    "MaxLineCurr": 4.0, "MaxPhaseCurr": 4.0, "CustomMaxLineCurr": 4.0,
    "CustomMaxPhaseCurr": 4.0, "lineCurrent": 4.0, "deci_volts": 10.0,
    "modulation": 128.0, "MaxSpeed": 4.0, "RatedSpeed": 4.0, "MidSpeed": 4.0,
    "LowSpeed": 4.0, "BackSpeed": 4.0, "StopBackCurr": 4.0, "MaxBackCurr": 4.0,
    "ThrottleLow": 20.0, "ThrottleHigh": 20.0,
}
UNIT = {
    10.0: "V", 100.0: "V", 4.0: "A", 20.0: "V",
}
# safety-critical names
SAFETY = {"MaxLineCurr","MaxPhaseCurr","CustomMaxLineCurr","CustomMaxPhaseCurr",
          "RatedVoltage","LowVolProtect","HighVolProtect","MaxSpeed","PolePairs",
          "MotorTempProtect","MosTempProtect"}

# block -> (section, readOnly)
SEC = {
 0x00:("FixedParas",False),0x06:("Functions",False),0x0C:("PID Paras",False),
 0x12:("Parameters",False),0x18:("Parameters",False),0x1E:("Functions",False),
 0x24:("Parameters",False),0x2A:("Parameters",False),0x30:("Parameters",False),
 0x5D:("Parameters",False),0x63:("FixedParas",False),0x69:("Display",False),
 0x7C:("Display",False),0x82:("Protect",False),0x88:("Energy Regenerate",False),
 0x8E:("Energy Regenerate",False),0x94:("Ratios in Gear",False),0x9A:("Ratios in Gear",False),
 0xA0:("Product",True),0xA6:("Product",True),0xAC:("Functions",False),0xB2:("Functions",False),
 0xB8:("Functions",False),0xBE:("Functions",False),0xC4:("Functions",False),0xCA:("Functions",False),
 0xD0:("Display",False),0xD6:("Diagnostics",True),0xDC:("Diagnostics",True),
 0xE2:("Diagnostics",True),0xE8:("Diagnostics",True),0xEE:("Diagnostics",True),
 0xF4:("Diagnostics",True),0xFA:("Diagnostics",True),
}
def section_for(name, base):
    sec, _ = SEC.get(base, ("Parameters", False))
    n = name.lower()
    if any(k in n for k in ("volprotect","temprotect","temprestore","volrestore")): return "Protect"
    if "backcurr" in n or "backp" in n or n.startswith("ratio"): return "Energy Regenerate"
    if "version" in n or "modelname" in n or n.startswith("model"): return "Product"
    if "canconfig" in n or "speedmeter" in n: return "Display"
    if n in ("startki","midki","maxki","startkp","midkp","maxkp","speedki","speedkp","sppedkp"): return "PID Paras"
    return sec

hpp = open(HPP).read()
struct_re = re.compile(r'struct (Addr[0-9A-Fa-f]+)\s*\{(.*?)\n\}', re.S)
decl_re = re.compile(r'^\s*(u?int8_t|u?int16_t|int16_t|char|int)\s+(\w+)\s*(?:\[(\d+)\])?\s*(?::\s*(\d+))?\s*;(.*)$')
anchor_re = re.compile(r'^\s*//\s*(\d+)(?:\s*-\s*\d+)?\s*(?:,|$)')

def parse_scale(comment):
    c = re.sub(r"0x[0-9A-Fa-f]+", "", comment or "")
    m = re.search(r"/\s*([0-9]+(?:\.[0-9]+)?)", c)
    if m: return float(m.group(1))
    m = re.search(r"\*\s*0?\.([0-9]+)", c)
    if m: return round(1.0/float("0."+m.group(1)), 6)
    return None

fields = []
for sm in struct_re.finditer(hpp):
    base = int(sm.group(1)[4:], 16); byte = 2; bit = 0
    for line in sm.group(2).splitlines():
        a = anchor_re.match(line)
        if a: byte = int(a.group(1)); bit = 0; continue
        if re.match(r'^\s*//', line): continue
        d = decl_re.match(line)
        if not d: continue
        typ, name, arr, bits, comment = d.groups()
        if bits:
            w = int(bits)
            if bit + w > 8: byte += 1; bit = 0
            fields.append(dict(base=base,name=name,word=base+(byte-2)//2,kind='bit',
                               lohi='hi' if (byte-2)%2 else 'lo',shift=bit,mask=(1<<w)-1,
                               scale=parse_scale(comment)))
            bit += w
            if bit >= 8: byte += bit//8; bit %= 8
        else:
            if bit != 0: byte += 1; bit = 0
            if arr:
                fields.append(dict(base=base,name=name,word=base+(byte-2)//2,kind='str')); byte += int(arr)
            elif '16' in typ:
                fields.append(dict(base=base,name=name,word=base+(byte-2)//2,kind='i16',
                                   scale=parse_scale(comment))); byte += 2
            else:
                fields.append(dict(base=base,name=name,word=base+(byte-2)//2,kind='u8',
                                   lohi='hi' if (byte-2)%2 else 'lo',scale=parse_scale(comment))); byte += 1

def emit(f):
    if f['kind'] == 'str': return None
    name = f['name']
    if re.match(r'(unk|pad|reserved)', name, re.I): return None
    base = f['base']
    scale = SCALE.get(name, f.get('scale'))
    sec = section_for(name, base)
    ro = SEC.get(base, ("",False))[1]
    args = [f'name = "{name}"', f'addr = 0x{f["word"]:02X}', f'section = "{sec}"']
    if scale and scale != 1: args.append(f'scale = {scale}f')
    u = UNIT.get(scale)
    if u: args.append(f'unit = "{u}"')
    if f.get('lohi') == 'lo': args.append('isLoByte = true')
    if f.get('lohi') == 'hi': args.append('isHiByte = true')
    if f['kind'] == 'bit':
        args.append(f'bitMask = 0x{f["mask"]:02X}')
        if f['shift']: args.append(f'bitShift = {f["shift"]}')
    if ro: args.append('isReadOnly = true')
    if name in SAFETY: args.append('isSafetyCritical = true')
    return "        ParamDef(" + ", ".join(args) + "),"

rows = [r for r in (emit(f) for f in fields) if r]
print('''package com.bretthalliday.fdtuner.model

/**
 * GENERATED from jackhumbert fardriver.hpp — DO NOT hand-edit addresses (re-run the generator).
 * Word address / byte / bit positions are computed from the struct layout and validated.
 * Scales: curated where known + best-effort from comments; verify unitless ones.
 * Requires a SECTION_DIAGNOSTICS = "Diagnostics" constant (read-only state/telemetry).
 */
object GeneratedParamDefs {
    val all: List<ParamDef> = listOf(''')
print("\n".join(rows))
print("    )\n}")
sys.stderr.write(f"emitted {len(rows)} ParamDefs\n")
