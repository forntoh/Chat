#!/usr/bin/env python3
"""Prepare fresh POM/docs over verified source-identical artifacts, locally and unsigned."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
from verify_packaging import MODULES, NS, VERSION, require, verify as verify_packaging
from verify_central import ALGORITHMS, verify, verify_bundle

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def save(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--staged', required=True, type=Path, help='Prior verified evidence directory with inventory/provenance/candidate')
    parser.add_argument('--output', required=True, type=Path, help='New local directory')
    args = parser.parse_args()
    commit, tree = git('rev-parse', 'HEAD'), git('rev-parse', 'HEAD^{tree}')
    require(Path.cwd().resolve() == ROOT, 'Run from repository root')
    require(commit == args.commit and not git('status', '--porcelain'), 'Expected exact commit and clean source')
    require('://' not in str(args.output), 'Output must be a local path')
    output, staged = args.output.resolve(), args.staged.resolve()
    require(not ROOT.is_relative_to(output) and not staged.is_relative_to(output), 'Unsafe output directory')
    previous = json.loads((staged / 'candidate.json').read_text())
    provenance = {e['path']: e for e in json.loads((staged / 'provenance.json').read_text())}
    inventory = verify_packaging(staged / 'repository')
    require(json.loads(json.dumps(inventory)) == json.loads((staged / 'inventory.json').read_text()), 'Prior inventory changed')
    # The only changed build input may be this publication convention. Runtime inputs stay identical.
    bindings = []
    for module in MODULES:
        builds = {e['artifactBuildCommit'] for e in provenance.values() if e['path'].split('/')[3].split('-')[1] == module}
        for built in sorted(builds):
            for path in (f'chat/{module}', 'gradle', 'gradle.properties', 'settings.gradle.kts', 'build.gradle.kts', 'gradlew', 'gradlew.bat', 'LICENSE', 'NOTICE'):
                before, after = git('rev-parse', f'{built}:{path}'), git('rev-parse', f'{commit}:{path}')
                require(before == after, f'Reused source input changed: {built}:{path}')
                bindings.append({'buildCommit': built, 'path': path, 'gitObject': before})
            changed = git('diff', '--name-only', built, commit, '--', 'build-logic').splitlines()
            require(set(changed) <= {'build-logic/src/main/kotlin/chat.staging.gradle.kts'}, 'Unreviewed build-logic change prevents reuse')
    output.mkdir(parents=True, exist_ok=False)
    repository = output / 'repository'
    repository.mkdir()
    record = {'commit': commit, 'tree': tree, 'status': 'INCOMPLETE', 'signed': False, 'publishedRemotely': False, 'reusedEvidence': str(staged), 'priorCandidate': previous}
    save(output / 'candidate.json', record)
    save(output / 'source-bindings.json', bindings)
    save(output / 'prior-inventory.json', inventory)
    save(output / 'prior-provenance.json', list(provenance.values()))
    command = ['./gradlew', *[f':chat:{m}:prepareCentralMetadata' for m in MODULES], '--console=plain']
    record['command'] = command
    save(output / 'candidate.json', record)
    with (output / 'gradle.log').open('w') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
    require(result.returncode == 0, f'Gradle failed; retain incomplete output and use fresh directory: {output}')
    origins = {}
    for entry in inventory['files']:
        relative = Path(entry['path'])
        # Central bundles contain exact-version files only, never repository-level maven-metadata.xml.
        if relative.parts[-2] != VERSION or '.pom' in relative.name:
            continue
        source = staged / 'repository' / relative
        require(hashlib.sha256(source.read_bytes()).hexdigest() == entry['sha256'], f'Reused file changed: {relative}')
        destination = repository / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        origins[str(relative)] = {'kind': 'reused', 'source': str(source), 'artifactBuildCommit': provenance[str(relative)]['artifactBuildCommit']}
    count = 0
    for module in MODULES:
        documentation = ROOT / 'chat' / module / 'build/libs' / f'{module}-{VERSION}-javadoc.jar'
        for pom in (ROOT / 'chat' / module / 'build/publications').glob('*/pom-default.xml'):
            xml = ET.parse(pom).getroot()
            artifact = xml.findtext('m:artifactId', namespaces=NS)
            destination = repository / 'dev/forntoh/chat' / artifact / VERSION
            require(destination.is_dir(), f'Unexpected generated publication: {artifact}')
            stem = destination / f'{artifact}-{VERSION}'
            for source, target in ((pom, Path(str(stem) + '.pom')), (documentation, Path(str(stem) + '-javadoc.jar'))):
                shutil.copyfile(source, target)
                origins[str(target.relative_to(repository))] = {'kind': 'fresh', 'artifactBuildCommit': commit, 'source': str(source)}
                for algorithm in ALGORITHMS:
                    sidecar = Path(str(target) + '.' + algorithm)
                    sidecar.write_text(hashlib.new(algorithm, target.read_bytes()).hexdigest())
                    origins[str(sidecar.relative_to(repository))] = {'kind': 'fresh', 'artifactBuildCommit': commit, 'source': str(source)}
            count += 1
    require(count == 25, 'Expected 25 freshly generated POMs')
    require(git('rev-parse', 'HEAD') == commit and not git('status', '--porcelain'), 'Source changed during preparation')
    result = verify(repository, commit)
    save(output / 'inventory.json', result)
    save(output / 'provenance.json', [dict(e, **origins[e['path']]) for e in result['files']])
    (output / 'SHA256SUMS').write_text(''.join(f"{e['sha256']}  repository/{e['path']}\n" for e in result['files']))
    bundle = output / 'central-UNSIGNED.zip'
    with zipfile.ZipFile(bundle, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for entry in result['files']:
            archive.write(repository / entry['path'], entry['path'])
    save(output / 'bundle-audit.json', verify_bundle(bundle, result))
    record.update(status='UNSIGNED_NOT_SUBMISSION_READY', externalConsumerCompilation='UNVERIFIED', provenanceCounts=dict(Counter(o['kind'] for o in origins.values())))
    save(output / 'candidate.json', record)
    print(f'Local unsigned preparation verified: {output}; NOT submission-ready')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
