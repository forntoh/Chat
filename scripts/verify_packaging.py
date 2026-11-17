#!/usr/bin/env python3
"""Verify the local publication boundary; never resolve or publish remotely."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

GROUP = "dev.forntoh.chat"
VERSION = "0.0.1"
MODULES = ("core", "ui", "material3", "actions", "media")
TARGETS = ("", "-android", "-jvm", "-iosarm64", "-iossimulatorarm64")
EXPECTED = {
    "core": {"core"},
    "ui": {"core", "ui"},
    "material3": {"core", "ui", "material3"},
    "actions": {"core", "ui", "actions"},
    "media": {"core", "ui", "media"},
}
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(repository):
    repository = repository.resolve()
    expected_ids = {"chat-" + module + suffix for module in MODULES for suffix in TARGETS}
    poms = sorted(repository.rglob("*.pom"))
    require(len(poms) == len(expected_ids), "Expected exactly 25 publications")
    require(len(list(repository.rglob("*.module"))) == 25, "Expected exactly 25 Gradle metadata files")
    graph = {module: set() for module in MODULES}
    external = {module: set() for module in MODULES}
    inventory = []
    for pom_path in poms:
        pom = ET.parse(pom_path).getroot()
        def field(name):
            return pom.findtext("m:" + name, namespaces=NS)
        artifact = field("artifactId")
        require(artifact in expected_ids, f"Unexpected artifact {artifact}")
        expected_ids.remove(artifact)
        require((field("groupId"), field("version")) == (GROUP, VERSION), f"Wrong coordinates: {pom_path}")
        require(pom_path == repository / GROUP.replace(".", "/") / artifact / VERSION / f"{artifact}-{VERSION}.pom", "Wrong repository layout")
        require(pom.findtext("m:licenses/m:license/m:name", namespaces=NS) == "Apache License, Version 2.0", f"Missing POM license: {artifact}")
        module = next(m for m in MODULES if artifact == "chat-" + m or artifact.startswith("chat-" + m + "-"))
        stem = pom_path.parent / f"{artifact}-{VERSION}"
        metadata = json.loads(Path(str(stem) + ".module").read_text())
        require(metadata["component"]["group"] == GROUP and metadata["component"]["version"] == VERSION, f"Wrong Gradle coordinates: {artifact}")
        dependencies = []
        for dep in pom.findall("m:dependencies/m:dependency", NS):
            dependencies.append((dep.findtext("m:groupId", namespaces=NS), dep.findtext("m:artifactId", namespaces=NS), dep.findtext("m:version", namespaces=NS)))
        variants = []
        for variant in metadata["variants"]:
            variants.append(variant["name"])
            for dep in variant.get("dependencies", []):
                dependencies.append((dep["group"], dep["module"], dep.get("version", {}).get("requires")))
            redirect = variant.get("available-at")
            if redirect:
                require(redirect["group"] == GROUP and redirect["version"] == VERSION, "Wrong variant redirect coordinates")
                target = (pom_path.parent / redirect["url"]).resolve()
                require(target.is_relative_to(repository) and target.is_file(), f"Missing redirected variant: {target}")
            for entry in variant.get("files", []):
                target = (pom_path.parent / entry["url"]).resolve()
                require(target.is_relative_to(repository) and target.is_file(), f"Missing variant artifact: {target}")
                payload = target.read_bytes()
                require(len(payload) == entry["size"], f"Wrong size: {target}")
                for algorithm in ("sha256", "sha512"):
                    require(hashlib.new(algorithm, payload).hexdigest() == entry[algorithm], f"Wrong metadata checksum: {target}")
        for group, name, version in dependencies:
            if group == GROUP:
                require(version == VERSION, f"Wrong internal dependency version: {name}")
                base = next((m for m in MODULES if name == "chat-" + m or name.startswith("chat-" + m + "-")), None)
                require(base is not None, f"Unexpected internal dependency: {name}")
                graph[module].add(base)
            else:
                require("candidate" not in group, f"Stale dependency namespace: {group}")
                external[module].add(f"{group}:{name}:{version}")
        extension = ".aar" if artifact.endswith("-android") else ".klib" if artifact.endswith(("-iosarm64", "-iossimulatorarm64")) else ".jar"
        binary = Path(str(stem) + extension)
        require(binary.is_file() and binary.stat().st_size > 0, f"Missing binary: {artifact}")
        if extension in (".jar", ".aar"):
            with zipfile.ZipFile(binary) as archive:
                for legal in ("LICENSE", "NOTICE"):
                    require(archive.read("META-INF/" + legal) == (Path(__file__).resolve().parents[1] / legal).read_bytes(), f"Missing binary {legal}: {artifact}")
        sources = Path(str(stem) + "-sources.jar")
        with zipfile.ZipFile(sources) as archive:
            names = archive.namelist()
            require(any(n.endswith(".kt") for n in names), f"Missing Kotlin sources: {artifact}")
            for legal in ("LICENSE", "NOTICE"):
                require(archive.read("META-INF/" + legal) == (Path(__file__).resolve().parents[1] / legal).read_bytes(), f"Missing/incorrect {legal}: {artifact}")
        inventory.append({"artifact": artifact, "variants": variants, "dependencies": sorted(set(dependencies)), "sources": sources.name, "kdoc": "KDoc in Kotlin sources; no rendered javadoc artifact"})
    closures = {}
    for selection, expected in EXPECTED.items():
        seen = set()
        pending = [selection]
        while pending:
            module = pending.pop()
            if module not in seen:
                seen.add(module)
                pending.extend(graph[module])
        require(seen == expected, f"Wrong {selection} selection closure: {seen}; expected {expected}")
        deps = set().union(*(external[module] for module in seen))
        if selection != "material3":
            require(not any("material3" in dep or "icons-tabler" in dep for dep in deps), f"Forced optional theme: {selection}")
        closures[selection] = {"modules": sorted(seen), "externalDependencies": sorted(deps)}
    files = []
    for path in sorted(repository.rglob("*")):
        if path.is_file():
            require(not path.is_symlink(), f"Symlink in repository: {path}")
            if path.suffix in (".sha256", ".sha512", ".sha1", ".md5"):
                algorithm = path.suffix[1:]
                original = path.with_suffix("")
                require(original.is_file() and hashlib.new(algorithm, original.read_bytes()).hexdigest() == path.read_text().strip(), f"Wrong sidecar checksum: {path}")
            files.append({"path": str(path.relative_to(repository)), "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    return {"group": GROUP, "version": VERSION, "publications": inventory, "selections": closures, "files": files, "externalConsumerCompilation": "UNVERIFIED"}


if __name__ == "__main__":
    try:
        print(json.dumps(verify(Path(sys.argv[1])), indent=2))
    except (ValueError, KeyError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
