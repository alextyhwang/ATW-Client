import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('relocate', Path(__file__).parents[1] / 'relocate_mac_qt.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


class RelocationTests(unittest.TestCase):
    def test_framework_components_preserved(self):
        self.assertEqual(str(r.framework_suffix('/opt/qt/lib/QtCore.framework/Versions/A/QtCore')).replace('\\', '/'),
                         'QtCore.framework/Versions/A/QtCore')

    def test_all_native_locations_and_symlink_deduplication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            for name in ('runtime/java/lib/server/libjvm.dylib', 'a.app/Contents/PlugIns/platforms/cocoa',
                         'a.app/Contents/Frameworks/F.framework/Versions/B/F', 'data/native/helper'):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(bytes.fromhex('cffaedfe') + b'payload')
            (root / 'text').write_text('not native')
            self.assertEqual(len(r.discover(root)), 4)

    def test_resolution_stays_in_own_app(self):
        root = Path('/package')
        one = root / 'one.app/Contents/Frameworks/Q.framework/Versions/A/Q'
        two = root / 'two.app/Contents/Frameworks/Q.framework/Versions/A/Q'
        self.assertEqual(r.target_for('/host/Q.framework/Versions/A/Q',
                         root / 'one.app/Contents/MacOS/main', root / 'one.app', [one, two]), one)
        self.assertIsNone(r.target_for('/host/missing', one, root / 'one.app', [one, two]))

    def test_ambiguous_dylib_fails(self):
        with self.assertRaises(ValueError):
            r.target_for('@rpath/libx.dylib', Path('/pkg/main'), Path('/pkg'),
                         [Path('/pkg/a/libx.dylib'), Path('/pkg/b/libx.dylib')])

    def test_load_command_parser_excludes_identity(self):
        output = '''Load command 0
 cmd LC_ID_DYLIB
 name /host/libx.dylib (offset 24)
Load command 1
 cmd LC_LOAD_DYLIB
 name @rpath/QtCore.framework/Versions/A/QtCore (offset 24)
Load command 2
 cmd LC_RPATH
 path /host/qt/lib (offset 12)
'''
        with patch.object(r, 'run', return_value=output):
            self.assertEqual(r.metadata(Path('binary')), (['@rpath/QtCore.framework/Versions/A/QtCore'],
                                                         ['/host/qt/lib'], '/host/libx.dylib'))


if __name__ == '__main__':
    unittest.main()
