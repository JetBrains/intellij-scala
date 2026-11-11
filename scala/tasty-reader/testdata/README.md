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
corpus.

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
anything. On this corpus, a successful dry run reports 103 fixtures and zero
byte differences. Review any intentional binary diff before committing it.

The former `compile.sh` and `clean.sh` deleted checked-in fixtures before they
could confirm successful regeneration. The Scala CLI command replaces them.

For the broader decompiler testing model, see
[SCL-24777](https://youtrack.jetbrains.com/issue/SCL-24777/Decompiler-tests-documentation).
