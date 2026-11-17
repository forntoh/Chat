#!/usr/bin/env python3
"""Prepare an unsigned candidate in a new local directory, without remote publishing."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

from verify_packaging import MODULES, verify


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True, help="Exact full committed candidate SHA")
    parser.add_argument("--output", required=True, type=Path, help="New local directory; must not exist")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if Path.cwd().resolve() != root:
        sys.exit("Run from the repository root")
    commit = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    if args.commit != commit or git("status", "--porcelain"):
        sys.exit("Expected exact candidate commit and a clean index/worktree")
    output = args.output.resolve()
    # Reject URLs as well as existing paths; mkdir is the collision guard.
    if "://" in str(args.output) or output == root or root.is_relative_to(output):
        sys.exit("Output must be a new local candidate directory")
    output.mkdir(parents=True, exist_ok=False)
    for name in ("LICENSE", "NOTICE"):
        shutil.copyfile(root / name, output / name)
    repository = output / "repository"
    repository.mkdir()
    tasks = [f":chat:{module}:publishAllPublicationsToStagingRepository" for module in MODULES]
    tasks += [f":sample:{task}" for task in ("compileKotlinJvm", "compileAndroidMain", "compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")]
    command = ["./gradlew", *tasks, f"-PchatStagingDirectory={repository}", "--console=plain"]
    record = {"commit": commit, "tree": tree, "command": command, "status": "INCOMPLETE", "signed": False, "publishedRemotely": False}
    record_path = output / "candidate.json"
    record_path.write_text(json.dumps(record, indent=2) + "\n")
    print(f"Staging {commit} into {repository}", flush=True)
    with (output / "gradle.log").open("w") as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        sys.exit(f"Packaging failed; partial output retained at {output}; inspect gradle.log and use a new directory to retry")
    if git("rev-parse", "HEAD") != commit or git("status", "--porcelain"):
        sys.exit("Source changed during packaging; candidate remains INCOMPLETE")
    inventory = verify(repository)
    (output / "inventory.json").write_text(json.dumps(inventory, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(f"{entry['sha256']}  repository/{entry['path']}\n" for entry in inventory["files"]))
    record["status"] = "LOCAL_VERIFIED"
    record["externalConsumerCompilation"] = "UNVERIFIED"
    record_path.write_text(json.dumps(record, indent=2) + "\n")
    print(f"Local packaging verified: {len(inventory['publications'])} publications, five selection closures. Evidence: {output}")


if __name__ == "__main__":
    main()
