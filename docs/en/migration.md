# Migration to Graph Atlas 0.5

[Deutsch](../de/migration.md) · [Documentation](index.md).

The working product name is Graph Atlas. The executable remains `graph-repository.jar`, the Maven parent `graph-repository-parent`, Java packages `ir.graph.repo` and environment variables `GR_`. This avoids silently breaking deployment scripts. English remains the first-run language; saved German/Persian choices remain available.

## Forward compatibility is not safe downgrade

The storage engine now initializes `releases` and `holds` tables in addition to existing tables. New code can read the earlier table set. Once new tables enter the WAL or snapshot, **do not run an older application against that modified directory**. Older code does not understand the new table names. Rollback means restoring an untouched pre-upgrade backup with the old binary, not merely changing the JAR.

The previous delivery had a legacy compatibility check. This delivery tests core persistence/restart, not a full Spring-runtime migration from every historical archive.

## Safe sequence

Stop the old application and take an offline backup outside its data directory. Preserve that backup separately.

```bash
# Old service stopped; old GR_HOME and old executable.
java -jar /path/to/old-application.jar --backup /secure-backups/before-atlas.zip

python scripts/restore.py /secure-backups/before-atlas.zip ./data-atlas-test
export GR_HOME="$PWD/data-atlas-test"

# This build must succeed before testing the copied data.
bash scripts/build.sh
java -jar dist/graph-repository.jar --verify
```

Run the new server against the copy, check native clients and the explicit release workflow, then rehearse restoring a new-version backup. Do not run two processes against the same directory. Recheck TLS, secure cookies, `GR_PUBLIC_URL`, service permissions and backup exclusions.

## Signing identity is part of the backup

A new signing identity is created when first needed. `GR_HOME/evidence-key.json` contains a private key and belongs with the data backup, not the source repository. The backup/restore tools retain it, with private file permissions where supported.

Keep backups encrypted and separately access-controlled. Losing the key after signing releases makes signed operations fail rather than silently substituting a new identity. Rotation is not implemented. For disaster recovery, restore the matching key and data together, verify the public fingerprint and validate existing receipts.

All capsule states pin files. A revoked capsule still retains history and content. Plan disk capacity before enabling widespread capture; there is no automatic expiry or capsule-purge operation.

Full runtime acceptance, not syntax parsing alone, is required before switching a production service.
