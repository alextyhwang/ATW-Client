"""Create a package-local ATW game process from Java's unchanged launcher code.

Windows only; stdlib only. The original java/javaw executables stay intact.
Only the copy's PE presentation resources (icon/version strings) are replaced.
"""
import argparse
import ctypes
from ctypes import wintypes
from pathlib import Path
import os
import shutil
import struct


def block(key, value=b"", children=(), text=False):
    head=struct.pack("<HHH",0,len(value)//2 if text else len(value),int(text))
    result=head+key.encode("utf-16-le")+b"\0\0"
    result+=b"\0"*((-len(result))%4); result+=value
    for child in children:
        result+=b"\0"*((-len(result))%4); result+=child
    return struct.pack("<H",len(result))+result[2:]


def version_resource():
    strings={"CompanyName":"ATW Client", "FileDescription":"ATW Client",
        "ProductName":"ATW Client", "InternalName":"ATW Client",
        "OriginalFilename":"ATW Client.exe", "FileVersion":"1.0.0.0",
        "ProductVersion":"1.0.0.0", "Comments":"ATW Client game host; bundled OpenJDK 17 launcher"}
    table=block("040904B0",children=[block(k,(v+"\0").encode("utf-16-le"),text=True) for k,v in strings.items()])
    fixed=struct.pack("<13I",0xFEEF04BD,0x10000,0x10000,0,0x10000,0,0x3f,0,0x40004,1,0,0,0)
    return block("VS_VERSION_INFO",fixed,[block("StringFileInfo",children=[table]),
        block("VarFileInfo",children=[block("Translation",struct.pack("<HH",0x409,1200))])])


def brand(java_bin, icon):
    if os.name != "nt": raise ValueError("Windows resource editing requires Windows")
    java_bin = java_bin.resolve(strict=True)
    source=java_bin/"javaw.exe"; destination=java_bin/"ATW Client.exe"
    stage=java_bin/".atw-game-host.tmp.exe"
    shutil.copy2(source,stage)
    kernel=ctypes.WinDLL("kernel32",use_last_error=True)
    kernel.BeginUpdateResourceW.argtypes=[wintypes.LPCWSTR,wintypes.BOOL]
    kernel.BeginUpdateResourceW.restype=wintypes.HANDLE
    kernel.UpdateResourceW.argtypes=[wintypes.HANDLE,ctypes.c_void_p,ctypes.c_void_p,wintypes.WORD,ctypes.c_void_p,wintypes.DWORD]
    kernel.EndUpdateResourceW.argtypes=[wintypes.HANDLE,wintypes.BOOL]
    handle=kernel.BeginUpdateResourceW(str(stage),False)
    if not handle: raise ctypes.WinError(ctypes.get_last_error())
    try:
        def update(kind,name,data):
            buffer=ctypes.create_string_buffer(data)
            if not kernel.UpdateResourceW(handle,kind,name,0x409,buffer,len(data)):
                raise ctypes.WinError(ctypes.get_last_error())
        ico=icon.read_bytes(); count=struct.unpack_from("<H",ico,4)[0]
        entries=[]
        for index in range(count):
            width,height,colors,reserved,planes,bits,size,offset=struct.unpack_from("<BBBBHHII",ico,6+index*16)
            resource_id=2000+index; update(3,resource_id,ico[offset:offset+size])
            entries.append(struct.pack("<BBBBHHIH",width,height,colors,reserved,planes,bits,size,resource_id))
        update(14,1,struct.pack("<HHH",0,1,count)+b"".join(entries))
        update(16,1,version_resource())
        if not kernel.EndUpdateResourceW(handle,False): raise ctypes.WinError(ctypes.get_last_error())
        handle=None
        os.replace(stage,destination)
    finally:
        if handle: kernel.EndUpdateResourceW(handle,True)
        if stage.exists(): stage.unlink()
    print("Prepared ATW Client.exe game host; original Java launchers preserved")


if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("java_bin",type=Path)
    parser.add_argument("--icon",type=Path,default=Path(__file__).resolve().parents[1]/"res/minecraft.ico")
    args=parser.parse_args(); brand(args.java_bin,args.icon)
