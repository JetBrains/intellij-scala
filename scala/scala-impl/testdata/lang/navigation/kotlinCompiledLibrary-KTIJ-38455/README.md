# KTIJ-38455 Kotlin Navigation Test Library

This directory stores the prebuilt Kotlin library artifacts used by:

- `KotlinCrossLanguageGoToDeclaration_WithCompiledLibraryDependencyTest`

Artifacts:

- `kotlin-nav-lib.jar` (compiled classes)
- `kotlin-nav-lib-sources.jar` (attached sources)

Original sources are stored under `src/`.

## Rebuild

Example rebuild using Kotlin `1.9.22`:

```bash
cd community/scala/scala-impl/testdata/lang/navigation/kotlinCompiledLibrary-KTIJ-38455
rm -rf out
mkdir -p out/classes

kotlinc src/org/example/StartupActivity.kt -d out/classes
jar cf kotlin-nav-lib.jar -C out/classes .
jar cf kotlin-nav-lib-sources.jar -C src .
```
