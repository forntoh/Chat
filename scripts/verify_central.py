#!/usr/bin/env python3
"""Audit an UNSIGNED local Central layout. This does not establish Portal acceptance."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
from verify_packaging import GROUP, MODULES, NS, TARGETS, VERSION, require, verify as verify_packaging

ALGORITHMS = ('md5', 'sha1', 'sha256', 'sha512')


def verify(repository, source_commit):
    require(re.fullmatch('[0-9a-f]{40}', source_commit), 'Expected immutable release-source commit')
    repository = repository.resolve()
    for path in repository.rglob('*'):
        require(not path.is_symlink(), f'Symlink in repository: {path}')
        if path.is_file():
            parts = path.relative_to(repository).parts
            ids = {'chat-' + m + t for m in MODULES for t in TARGETS}
            require(len(parts) == 6 and parts[:3] == ('dev', 'forntoh', 'chat') and parts[3] in ids and parts[4] == VERSION and parts[5].startswith(parts[3] + '-' + VERSION), f'Unsafe repository path: {path}')
            require(path.suffix != '.asc', 'Unsigned preparation must contain no signatures')
    allowed_suffixes = {'.pom', '.module', '.jar', '.aar', '.klib', '-sources.jar', '-javadoc.jar', '-kotlin-tooling-metadata.json'}
    for path in repository.rglob('*'):
        if path.is_file():
            base = path.with_suffix('') if path.suffix[1:] in ALGORITHMS else path
            stem = base.parent.parent.name + '-' + VERSION
            require(base.name.removeprefix(stem) in allowed_suffixes, f'Unexpected Central file: {path}')
    for pom_path in sorted(repository.rglob('*.pom')):
        pom = ET.parse(pom_path).getroot()
        def field(name):
            return pom.findtext('m:' + name, namespaces=NS)
        for name in ('name', 'description', 'url', 'licenses/m:license/m:url', 'scm/m:url', 'scm/m:connection', 'scm/m:developerConnection'):
            require(field(name), f'Missing POM {name}: {pom_path}')
        developers = pom.findall('m:developers/m:developer', NS)
        require(len(developers) == 1, f'Missing/incorrect developer: {pom_path}')
        dev = developers[0]
        for key, expected in (('id', 'forntoh'), ('name', 'Thomas Forntoh'), ('url', 'https://github.com/forntoh')):
            require(dev.findtext('m:' + key, namespaces=NS) == expected, f'Incorrect developer {key}: {pom_path}')
        require(dev.find('m:email', NS) is None, 'Unexpected developer email')
        documentation = pom_path.with_name(pom_path.stem + '-javadoc.jar')
        require(documentation.is_file(), f'Missing documentation: {documentation}')
        with zipfile.ZipFile(documentation) as archive:
            readme = archive.read('README.md').decode()
            for text in ('no rendered API documentation', f'https://github.com/forntoh/Chat/blob/{source_commit}/README.md', f'https://github.com/forntoh/Chat/tree/{source_commit}/docs'):
                require(text in readme, f'Incorrect documentation: {documentation}')
            for legal in ('LICENSE', 'NOTICE'):
                require(archive.read('META-INF/' + legal) == (Path(__file__).resolve().parents[1] / legal).read_bytes(), f'Incorrect documentation {legal}')
    inventory = verify_packaging(repository)
    for entry in inventory['files']:
        path = repository / entry['path']
        if path.suffix[1:] not in ALGORITHMS:
            for algorithm in ALGORITHMS:
                require(Path(str(path) + '.' + algorithm).is_file(), f'Missing checksum: {path}.{algorithm}')
    for publication in inventory['publications']:
        publication['documentation'] = publication['artifact'] + '-' + VERSION + '-javadoc.jar'
        publication['kdoc'] = 'KDoc in Kotlin sources; documentation pointer JAR, no rendered API documentation'
    inventory.update(status='UNSIGNED_NOT_SUBMISSION_READY', signed=False, publishedRemotely=False, documentationSource=source_commit)
    return inventory


def verify_bundle(bundle, inventory):
    expected = {entry['path']: entry for entry in inventory['files']}
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        for name in names:
            path = PurePosixPath(name)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in name and str(path) == name, f'Unsafe bundle path: {name}')
            require((archive.getinfo(name).external_attr >> 16) & 0o170000 != 0o120000, f'Symlink in bundle: {name}')
        require(len(names) == len(set(names)) and set(names) == set(expected), 'Bundle inventory mismatch')
        for name in names:
            payload = archive.read(name)
            require(len(payload) == expected[name]['bytes'] and hashlib.sha256(payload).hexdigest() == expected[name]['sha256'], f'Bundle checksum mismatch: {name}')
    return {'status': 'UNSIGNED_NOT_SUBMISSION_READY', 'files': len(expected), 'sha256': hashlib.sha256(Path(bundle).read_bytes()).hexdigest()}


if __name__ == '__main__':
    try:
        inventory = verify(Path(sys.argv[1]), sys.argv[2])
        if len(sys.argv) > 3:
            print(json.dumps(verify_bundle(Path(sys.argv[3]), inventory), indent=2))
        else:
            print(json.dumps(inventory, indent=2))
    except (ValueError, KeyError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
