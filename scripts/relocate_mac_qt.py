#!/usr/bin/env python3
"""Relocate and audit every staged Mach-O; never alter dependency source trees."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

MAGICS = {bytes.fromhex(x) for x in ('cffaedfe', 'feedfacf', 'cefaedfe', 'feedface',
                                    'cafebabe', 'bebafeca', 'cafebabf', 'bfbafeca')}


def run(*args):
    return subprocess.run(list(map(str, args)), check=True, capture_output=True, text=True).stdout


def inside(path, root):
    return path == root or root in path.parents


def discover(root):
    result = []
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            if path.is_symlink():
                if os.path.isabs(os.readlink(path)) or not inside(path.resolve(strict=True), root):
                    raise ValueError(f'Nonportable symlink: {path}')
        for name in files:
            path = Path(directory) / name
            if not path.is_symlink():
                with path.open('rb') as stream:
                    if stream.read(4) in MAGICS:
                        result.append(path)
    return sorted(result)


def system(name):
    return name.startswith(('/usr/lib/', '/System/Library/'))


def framework_suffix(name):
    parts = Path(name).parts
    for index, part in enumerate(parts):
        if part.endswith('.framework'):
            return Path(*parts[index:])
    return None


def metadata(path):
    # LC_ID_DYLIB is not a dependency; parsing load commands avoids otool -L's ambiguity.
    blocks = run('/usr/bin/otool', '-l', path).split('Load command ')[1:]
    deps, rpaths, identity = [], [], None
    for block in blocks:
        lines = [line.strip() for line in block.splitlines()]
        command = next((line[4:] for line in lines if line.startswith('cmd ')), '')
        if command == 'LC_RPATH':
            rpaths.append(next(line[5:].rsplit(' (offset ', 1)[0] for line in lines if line.startswith('path ')))
        elif command in {'LC_LOAD_DYLIB', 'LC_LOAD_WEAK_DYLIB', 'LC_REEXPORT_DYLIB', 'LC_LOAD_UPWARD_DYLIB', 'LC_ID_DYLIB'}:
            name = next(line[5:].rsplit(' (offset ', 1)[0] for line in lines if line.startswith('name '))
            if command == 'LC_ID_DYLIB':
                identity = name
            else:
                deps.append(name)
    return deps, rpaths, identity


def scope_for(path, root):
    for parent in path.parents:
        if parent.suffix == '.app':
            return parent
    java = root / 'runtime/java'
    return java if inside(path, java) else root


def target_for(name, path, scope, binaries):
    if name.startswith('@loader_path/'):
        candidate = path.parent / name[len('@loader_path/'):]
        if candidate.exists() and inside(candidate.resolve(), scope):
            return candidate.resolve()
    suffix = framework_suffix(name)
    matches = [item for item in binaries if inside(item, scope) and
               (item.as_posix().endswith('/' + suffix.as_posix()) if suffix else item.name == Path(name).name)]
    # Minecraft natives are loaded beside the package Java runtime. Resolve a
    # unique package-local target when their @rpath points across the package,
    # while keeping app bundles isolated from each other.
    if not matches:
        matches = [item for item in binaries if inside(item, root_for_binaries(binaries)) and
                   (item.as_posix().endswith('/' + suffix.as_posix()) if suffix else item.name == Path(name).name)]
    if len(matches) == 1:
        return matches[0]
    if len(matches) > 1:
        raise ValueError(f'Ambiguous dependency {name} in {path}')
    return None


def root_for_binaries(binaries):
    root = binaries[0]
    for item in binaries[1:]:
        while root != root.parent and not inside(item, root):
            root = root.parent
    return root


def external_source(name, path, rpaths):
    candidates = [Path(name)] if name.startswith('/') else []
    if name.startswith('@rpath/'):
        for rpath in rpaths:
            expanded = rpath.replace('@loader_path', str(path.parent))
            if expanded.startswith('/'):
                candidates.append(Path(expanded) / name[len('@rpath/'):])
        # Homebrew's deployed Qt dylibs may retain an @rpath dependency but lose
        # its originating rpath. Search only explicit local Homebrew lib roots.
        candidates += list(Path('/opt/homebrew/opt').glob('*/lib/' + Path(name).name))
    existing = {p.resolve() for p in candidates if p.is_file()}
    if len(existing) != 1:
        raise ValueError(f'Cannot uniquely source dependency {name} of {path}')
    source = existing.pop()
    if not str(source).startswith(('/opt/homebrew/', '/Users/')):
        raise ValueError(f'Unsupported external dependency source: {source}')
    return source


def plan(root, allow_copy):
    binaries = discover(root)
    index = 0
    while index < len(binaries):
        path = binaries[index]
        deps, rpaths, identity = metadata(path)
        scope = scope_for(path, root)
        for name in deps:
            if system(name) or target_for(name, path, scope, binaries):
                continue
            if not allow_copy or scope.suffix != '.app':
                raise ValueError(f'Unresolved dependency {name} in {path}')
            source = external_source(name, path, rpaths)
            suffix = framework_suffix(str(source))
            destination_root = scope / 'Contents/Frameworks'
            destination_root.mkdir(exist_ok=True)
            if suffix:
                source_root = source.parents[len(suffix.parts) - 2]
                destination = destination_root / suffix.parts[0]
                if destination.exists():
                    raise ValueError(f'Incomplete existing framework: {destination}')
                shutil.copytree(source_root, destination, symlinks=True)
            else:
                destination = destination_root / source.name
                if destination.exists():
                    raise ValueError(f'Dependency destination exists: {destination}')
                shutil.copy2(source, destination)
            binaries = discover(root)
            # Restart so newly copied dependencies and earlier consumers are checked.
            index = -1
            break
        index += 1
    return binaries


def main(root, audit_only=False):
    root = Path(root).resolve(strict=True)
    binaries = plan(root, not audit_only)
    changed = []
    for path in binaries:
        deps, rpaths, identity = metadata(path)
        scope = scope_for(path, root)
        edits = []
        for name in deps:
            if system(name):
                continue
            target = target_for(name, path, scope, binaries)
            if target is None:
                raise ValueError(f'Unresolved dependency: {name}')
            if scope.suffix != '.app':
                # Native archives are content-addressed by the launcher. Their
                # bytes must remain exactly as extracted; validating a
                # package-local @rpath target is sufficient here.
                continue
            local = '@loader_path/' + Path(os.path.relpath(target, path.parent)).as_posix()
            if name != local:
                edits += ['-change', name, local]
        # An install ID is metadata, not a load dependency. Game natives can
        # legitimately retain the upstream ID; only rewrite IDs for libraries
        # owned by our app bundles.
        if identity and scope.suffix == '.app':
            local_id = '@rpath/' + (framework_suffix(str(path)) or Path(path.name)).as_posix()
            if identity != local_id:
                edits += ['-id', local_id]
        # All dependencies now resolve directly from their loader; host rpaths
        # must not survive as an accidental fallback on the build machine.
        for rpath in dict.fromkeys(rpaths):
            if rpath.startswith('/'):
                edits += ['-delete_rpath', rpath]
        if edits:
            if audit_only:
                raise ValueError(f'Nonrelocated load commands: {path}')
            run('/usr/bin/install_name_tool', *edits, path)
            run('/usr/bin/codesign', '--force', '--sign', '-', '--preserve-metadata=entitlements', path)
            changed.append(path)
    if not audit_only:
        # Framework/app resource seals must be refreshed after their inner code.
        bundles = sorted([p for p in root.rglob('*') if not p.is_symlink() and
                          p.is_dir() and p.suffix in {'.framework', '.app'}],
                         key=lambda p: len(p.parts), reverse=True)
        for bundle in bundles:
            run('/usr/bin/codesign', '--force', '--sign', '-', bundle)
        main(root, True)
    else:
        for path in binaries:
            run('/usr/bin/codesign', '--verify', '--strict', path)
        print(f'Audit passed: {len(binaries)} Mach-O files; all dependencies package-local or Apple system')
    if not audit_only:
        print(f'Relocated and ad-hoc signed {len(changed)} Mach-O files')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root')
    parser.add_argument('--audit-only', action='store_true')
    args = parser.parse_args()
    main(args.root, args.audit_only)
