"""Synthetic ZIP/Mach-O regressions; no runtime or account payloads required."""

import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile


SPEC = importlib.util.spec_from_file_location(
    "inventory_mac_runtime", Path(__file__).resolve().parents[1] / "inventory_mac_runtime.py")
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


def macho(kind=6, cpu=0x0100000C):
    # One bounded LC_UUID command is sufficient for the structural validator.
    return (b"\xcf\xfa\xed\xfe" + struct.pack("<7I", cpu, 0, kind, 1, 24, 0, 0)
            + struct.pack("<2I", 0x1B, 24) + bytes(16))


class NativeArchiveTest(unittest.TestCase):
    def inspect(self, additions=None, remove=()):
        entries = {"libjcocoa.dylib": macho(), "liblwjgl.dylib": macho(),
                   "libopenalsoft.dylib": macho(), "libwebp-imageio64.dylib": macho(),
                   "rewind-audio-helper": macho(kind=2)}
        entries.update(additions or {})
        for name in remove:
            del entries[name]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            archive = root / "client-natives-macos-aarch64-v1_8.zip"
            with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as stream:
                for name, payload in entries.items():
                    stream.writestr(name, payload)
            return inventory.inspect_archive(
                root, archive, "arm64", True,
                [inventory.LIMITS["expandedBytesTotal"]], [0])

    def test_stock_lunar_layout_with_optional_families_absent(self):
        report = self.inspect()
        self.assertTrue(report["compatible"], report["diagnostics"])
        self.assertEqual(report["missingFamilies"], ["jinput", "jna"])
        helper = next(item for item in report["binaries"] if item["name"] == "rewind-audio-helper")
        self.assertTrue(helper["compatible"])
        self.assertEqual(helper["slices"][0]["fileType"], 2)

    def test_required_families_still_fail_when_missing(self):
        for name in ("liblwjgl.dylib", "libopenalsoft.dylib"):
            with self.subTest(name=name):
                report = self.inspect(remove=(name,))
                self.assertFalse(report["compatible"])
                self.assertIn("native_families_missing", [d["code"] for d in report["diagnostics"]])

    def test_optional_native_libraries_are_validated_when_present(self):
        for payload in (b"MZ" + bytes(64), macho(cpu=0x01000007), b"invalid"):
            with self.subTest(payload=payload[:4]):
                self.assertFalse(self.inspect({"libjinput-osx.jnilib": payload})["compatible"])
        self.assertTrue(self.inspect({"libjinput-osx.jnilib": macho(),
                                      "libjnidispatch.jnilib": macho()})["compatible"])

    def test_helper_requires_flat_matching_executable_and_known_name(self):
        for name, payload in (("rewind-audio-helper", macho()),
                              ("rewind-audio-helper", b"invalid"),
                              ("rewind-audio-helper", macho(kind=2, cpu=0x01000007)),
                              ("nested/rewind-audio-helper", macho(kind=2)),
                              ("unknown-helper", macho(kind=2))):
            with self.subTest(name=name, header=payload[:16]):
                self.assertFalse(self.inspect({name: payload})["compatible"])


if __name__ == "__main__":
    unittest.main()
