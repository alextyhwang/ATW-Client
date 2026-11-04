import importlib.util
import io
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location("branding",ROOT/"scripts/patch_lunar_branding.py")
branding=importlib.util.module_from_spec(spec); spec.loader.exec_module(branding)


class BrandingTests(unittest.TestCase):
    def test_only_display_constants_change_with_other_pool_entries_and_tail_preserved(self):
        strings=[b"Lunar Client \x01", b"https://api.lunarclientprod.com",b"com/moonsworth/lunar/client/Auth",b"refreshToken",b"LUNAR CLIENT"]
        pool=b"".join(b"\x01"+struct.pack(">H",len(s))+s for s in strings)
        header=b"\xca\xfe\xba\xbe\x00\x00\x00\x3d"+struct.pack(">H",len(strings)+1)
        tail=b"unchanged-methods-and-code"
        result, count=branding.rebrand_class(header+pool+tail)
        self.assertEqual(2,count)
        self.assertIn(b"ATW Client \x01",result); self.assertIn(b"ATW CLIENT",result)
        for value in strings[1:4]: self.assertIn(value,result)
        self.assertTrue(result.endswith(tail))
        self.assertEqual((result,0),branding.rebrand_class(result))

    def test_patch_preserves_runtime_entries_and_logo_geometry_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar=Path(tmp)/"lunar.jar"
            image=io.BytesIO(); Image.new("RGBA",(128,117),"white").save(image,"PNG")
            names=["assets/lunar/logo/logo-128x117.png","assets/lunar/animatedlogo/128/logo_128_star_1.png"]
            with zipfile.ZipFile(jar,"w") as z:
                for name in names: z.writestr(name,image.getvalue())
                z.writestr("config/auth.json",b'{"refreshToken":null,"url":"https://lunarclient.com"}')
            branding.patch(jar,ROOT/"res")
            with zipfile.ZipFile(jar) as z:
                self.assertEqual(b'{"refreshToken":null,"url":"https://lunarclient.com"}',z.read("config/auth.json"))
                for name in names:
                    self.assertEqual((128,117),Image.open(io.BytesIO(z.read(name))).size)
                self.assertIsNone(Image.open(io.BytesIO(z.read(names[1]))).getbbox())
            self.assertEqual([],branding.patch(jar,ROOT/"res"))

    def test_signed_jar_rejected_without_mutating_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar=Path(tmp)/"signed.jar"
            with zipfile.ZipFile(jar,"w") as z: z.writestr("META-INF/VENDOR.RSA",b"signature")
            original=jar.read_bytes()
            with self.assertRaisesRegex(ValueError,"signed"):
                branding.patch(jar,ROOT/"res")
            self.assertEqual(original,jar.read_bytes())

    def test_split_wordmark_only_changes_when_ui_admitted(self):
        value=b"LUNAR"
        original=b"\xca\xfe\xba\xbe\x00\x00\x00\x3d\x00\x02\x01\x00\x05"+value
        self.assertEqual((original,0),branding.rebrand_class(original))
        result,count=branding.rebrand_class(original,split_label=True)
        self.assertEqual(1,count); self.assertTrue(result.endswith(b"\x00\x03ATW"))

    def test_pause_menu_and_translation_values_change_without_keys_or_service_urls(self):
        value={'gui.lunar.options':'Lunar Options...', 'nested':['LUNAR CLIENT Settings',
            'https://lunarclient.com', 'Lunar Client', 'Lunar设置'], 'namespace':'lunar',
            'path':'com/moonsworth/lunar/client/Auth', 'refreshToken':None}
        result=branding.translate_values(value)
        self.assertEqual('ATW Options...',result['gui.lunar.options'])
        self.assertEqual('ATW CLIENT Settings',result['nested'][0])
        self.assertEqual('https://lunarclient.com',result['nested'][1])
        self.assertEqual('ATW Client',result['nested'][2])
        self.assertEqual('ATW设置',result['nested'][3])
        for key in ('namespace','path','refreshToken'): self.assertEqual(value[key],result[key])
        with tempfile.TemporaryDirectory() as tmp:
            jar=Path(tmp)/'lunar-lang.jar'
            with zipfile.ZipFile(jar,'w') as z: z.writestr('lang/lunar/en_US.json',__import__('json').dumps(value))
            branding.patch(jar,ROOT/'res')
            with zipfile.ZipFile(jar) as z:
                actual=__import__('json').loads(z.read('lang/lunar/en_US.json'))
                self.assertEqual(result,actual)


if __name__ == "__main__": unittest.main()
