# Scala 3 decompiler fixtures

`DecompilerTest3` reads the checked-in `.tasty` bytes. It does not compile the
`.scala` files or select a Scala SDK at test time. Each TASTy header records the
compiler that produced those bytes. The test checks the decompiled source name,
outline, and compiler options; `testFixtureCompilerVersions` also checks every
header against `fixture-groups.tsv` and rejects unclassified fixtures.

## Layout and version choice

| Directory | Meaning |
| --- | --- |
| `compiled/scala-3.0.0` | The historical corpus for features supported by Scala 3.0.0. Keep it pinned so changes to the reader remain compatible with those bytes. |
| `compiled/scala-3.6.1` | Source fixtures for named context bounds and deferred givens, which need a later compiler. |
| Other `compiled/scala-*` groups | Source filename metadata fixtures for the remaining released minor lines and the 3.10.1 patch. The manifest records each exact compiler release. |
| `libraries/borer-derivation_3-1.16.1` | `DerivationApi.tasty` copied unchanged from that binary JAR. Its TASTy header says Scala 3.3.5. |
| `libraries/scala-library-3.10.0` | `IndexedSeqOps.tasty` copied unchanged from that binary JAR. Its TASTy header says Scala 3.10.0-bin-nonbootstrapped. |

Within a `compiled` directory, paths still group Scala syntax features. A
`.scala` file is the compiler input and the expected decompiled outline; one
source may produce several `.tasty` files. Keep these sources self-contained.
Within `libraries`, paths mirror entries in the binary JAR. The checked-in
`DerivationApi.scala` is a manually simplified **expected outline**, not the
original borer source or an input to regeneration. Library fixtures may have no
`.scala` companion when a focused test checks only particular output.

For a new language feature, use a pinned compiler release that emits the
feature and put its sources and TASTy in a versioned `compiled` directory. For
a regression tied to a published library, copy the exact binary entry into an
artifact-versioned `libraries` directory and record its coordinate, TASTy
compiler version, and binary JAR SHA-256 in `fixture-groups.tsv`. Do not update
the 3.0.0 corpus to the current compiler merely to refresh its bytes. If a
behavior needs comparison across compiler releases, add a small, explicit set
of equivalent fixtures under those releases instead of duplicating the whole
corpus. Until the process is automated, add a metadata fixture and matrix entry
for each new minor line and for patch releases that change source metadata.

`metadata/SourceMetadata.scala` is intentionally identical in every compiled
group. It emits `Declaration.tasty`, so the test cannot derive the correct
source filename merely from the TASTy entry name. The explicit test matrix
covers every published Scala 3 minor line from 3.0 through 3.10; 3.9.0 and
3.10.0 bracket the source-file metadata change. At the time these fixtures were
added, Maven Central had no Scala 3.9.1 compiler release.
The `scala-library-3.10.0` entry has a different tooling version in its header
from the release compiler's fixture; the manifest records both exactly.

`types/Annotated.tasty` is retained and header-checked, but its decompiled
outline is not asserted yet. The corresponding test remains disabled because
type annotations are not printed by the decompiler (SCL-21207). It is the only
intentional outline-coverage exclusion in this corpus.

## Verify and regenerate

Run from the repository root:

```bash
scala-cli run community/scala/tasty-reader/scripts --server=false --jvm 17 -- --check
JAVA_HOME=/path/to/jdk17 scala-cli run community/scala/tasty-reader/scripts --server=false --jvm 17 -- --regenerate --dry-run
JAVA_HOME=/path/to/jdk17 scala-cli run community/scala/tasty-reader/scripts --server=false --jvm 17 -- --regenerate
scala-cli test community/scala/tasty-reader/scripts --server=false --jvm 17 --require-tests
```

The script is pinned to Scala 3.10.1. `--check` does not download fixture
inputs and reports fixture counts; Scala CLI may still resolve the script's
compiler and dependency on its first run. Regeneration downloads the pinned
Scala compiler distributions and binary JARs, unless local paths are provided
with `--compiler VERSION=PATH` and `--jar COORDINATE=PATH`. For releases
without a GitHub compiler archive, it compiles the fixture sources with Scala
CLI. Set `JAVA_HOME` to JDK 17 for regeneration because the Scala 3.0.0
compiler fails while reading JDK 25 class files; `--jvm 17` selects the JVM
for the Scala CLI script itself.
The command builds or extracts every group in temporary storage and checks the
resulting file inventory and TASTy headers before replacing checked-in bytes.
`--dry-run` performs all preparation and reports differences without replacing
anything. Review any intentional binary diff before committing it.
To add compiled fixtures, add the sources and a manifest group first, then
dry-run and run regeneration with `--allow-new`.

The former `compile.sh` and `clean.sh` deleted checked-in fixtures before they
could confirm successful regeneration. The Scala CLI command replaces them.

For the broader decompiler testing model, see
[SCL-24777](https://youtrack.jetbrains.com/issue/SCL-24777/Decompiler-tests-documentation).
