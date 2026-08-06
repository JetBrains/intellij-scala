import LocalRepoPackager.sbtDep
import coursier.core.Dependency
import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.idea.IntellijVersionUtils
import sbt.*

object Versions {
  val scalaVersion: String = "2.13.18"
  val scala3Version: String = "3.7.4"

  // ATTENTION: when updating `sbtVersion` also update it in `org.jetbrains.sbt.SbtVersion.Latest`
  // NOTE: sbt-launch won't be fetched on refresh.
  // run runtimeDependencies/update manually
  val sbtVersion: String = "1.12.14"
  val bloopVersion = "2.1.1"
  val zincVersion = "1.12.0"

  val nailgunVersion = "1.3.1"

  /**
   * ATTENTION: check the comment in [[Common.newProjectWithKotlin]] when updating this version.
   *            update `since-build` in plugin.xml if there are binary incompatible changes after update
   */
  val intellijVersion = "263.2767"

  def isNightlyIntellijVersion: Boolean = intellijVersion.count(_ == '.') == 1

  def pluginDependencySuffix: String = if (isNightlyIntellijVersion)
    s":$intellijVersion.0:nightly"
  else
    "" // intellijVersion will be automatically used by sbt-idea-plugin

  val (
    intellijVersion_ForManagedIntellijDependencies,
    intellijRepository_ForManagedIntellijDependencies,
  ) = detectIntellijArtifactVersionAndRepository(intellijVersion)

  private def detectIntellijArtifactVersionAndRepository(intellijVersion: String): (String, MavenRepository) = {
    val locationDescriptor = IntellijVersionUtils.detectArtifactLocation(BuildInfo(intellijVersion, IdeaCommunity), ".zip")
    val artifactVersion = locationDescriptor.artifactVersion
    val artifactUrl = locationDescriptor.url
    //println(s"""[detectIntellijArtifactVersionAndRepository] build number: $intellijVersion, artifact version: $artifactVersion, artifact url: $artifactUrl""")
    (artifactVersion, locationDescriptor.repository)
  }

  val junitVersion: String = "4.13.2"
  val junitParamsVersion: String = "1.1.1"
  val junitInterfaceVersion: String = "0.13.3"

  val bspVersion = "2.1.0"
  val sbtStructureVersion: String = "2026.2.5"
  val sbtIdeaShellVersion: String = "2025.3.0"

  // spray-json is a dependency of scala-compiler-indices-protocol. Make sure the versions match.
  val sprayJsonVersion = "1.3.6"
  val compilerIndicesVersion = "1.0.16"

  val java9rtExportVersion: String = "0.1.0"

  /**
   * For `"org.jetbrains.intellij.deps.languagetool" % "language-*"` dependencies
   *
   * This version should be the same as in `com.intellij.grazie.GraziePlugin.LanguageTool.version` (it's updated automatically by `UpdateVersions` script)
   * Note that in Grazie plugin they actually use custom language tool distributions (see com.intellij.grazie.GraziePlugin.LanguageTool.url)
   * However according to Peter Gromov it shouldn't be important for us and we can use maven dependencies.
   * Those custom distributions usually contain performance fixes and not the logic.
   */
  val LanguageToolVersion = "6.8.28"

  /**
   * Potentially automate the updating of this version in the future.
   */
  val HunspellDictionaryVersion = "0.2.359"

  object Sbt {
    val binary_0_13 = "0.13"
    val binary_1_0 = "1.0" // 1.0 is the binary version of sbt 1.x series
    val binary_2 = "2" // 2 is the binary version of sbt 2

    //sbt-structure-extractor is cross-published in a non-standard way,
    //against multiple 1.x versions, so it uses an exact binary version 1.x.
    //Versions 1.0-1.2 use 1.0, versions 1.3 and above use 1.3
    val structure_extractor_binary_0_13 = "0.13"
    val structure_extractor_binary_1_0 = "1.0"
    val structure_extractor_binary_1_3 = "1.3"
    //NOTE: try using "2.0" during local development when using `+publishLocal` in sbt-structure
    val structure_extractor_binary_2 = "2"
  }

  // If this value gets initialized to `Some(resolver)` then it needs to be used and the versions of the test framework
  // artifacts need to be equal to `intellijVersion`.
  // Otherwise, they need to be equal to `intellijVersion_ForManagedIntellijDependencies`.
  private val TeamCityArtifactsResolver: Option[Resolver] = {
    TeamCityCommunityUtil.getBuildIdForVersionSafe(intellijVersion)
      .toOption
      .flatten
      .map { buildId =>
        val mavenArtifactsUrl = s"https://buildserver.labs.intellij.net/guestAuth/app/rest/builds/id:$buildId/artifacts/content/maven-artifacts"
        val mavenArtifactPatterns = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        Resolver.url("teamcity-artifacts", url(mavenArtifactsUrl))(Patterns(isMavenCompatible = true, artifactPatterns = mavenArtifactPatterns))
      }
  }

  val IntellijTestFrameworkArtifactsResolver: Resolver =
    TeamCityArtifactsResolver.getOrElse(intellijRepository_ForManagedIntellijDependencies)

  val IntellijTestFrameworkVersion: String =
    if (TeamCityArtifactsResolver.isDefined) intellijVersion
    else intellijVersion_ForManagedIntellijDependencies
}

object Dependencies {

  import Versions.*

  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scala3Library: ModuleID = "org.scala-lang" % "scala3-library_3" % scala3Version
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scala3Compiler: ModuleID = "org.scala-lang" % "scala3-compiler_3" % scala3Version
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "2.4.0"
  val tastyCore: ModuleID = "org.scala-lang" % "tasty-core_3" % Versions.scala3Version
  val tastyInspector: ModuleID = "org.scala-lang" % "scala3-tasty-inspector_3" % Versions.scala3Version
  val scalaParallelCollections: ModuleID = "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains.scala" %% "sbt-structure-core" % sbtStructureVersion
  val coursierPaths: ModuleID = "io.get-coursier" % "coursier-paths" % "2.1.25-M26"
  // NOTE: current latest version is in https://github.com/unkarjedy/scalatest-finders.git repository

  val jetbrainsAnnotations: ModuleID = "org.jetbrains" % "annotations" % "26.1.0"

  val structureExtractor_0_13: Dependency = sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_0_13)
  val structureExtractor_1_0: Dependency = sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_1_0)
  val structureExtractor_1_3: Dependency = sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_1_3)
  val structureExtractor_2: Dependency = sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_2)

  /**
   * NOTE: JUnit 4 dependency is already available via intellij main jars.
   * It's bundled together with its transitive dependencies in single junit4.jar (in sdk_root/lib folder).
   * However, junit4.jar is excluded via excludeJarsFromPlatformDependencies.
   * Instead, we explicitly include junit dependency in all modules.
   * This is done because some modules are not intellij-based, and they explicitly define junit dependency anyway.
   * Due to imperfection of classpath construction, there might be multiple junit4 jrs in the final classpath.
   * (Both runtime and compilation time)
   */
  val junit: ModuleID = "junit" % "junit" % junitVersion
  val junitParams: ModuleID = "pl.pragmatists" % "JUnitParams" % junitParamsVersion
  val junitInterface: ModuleID = "com.github.sbt" % "junit-interface" % junitInterfaceVersion
  def jupiterInterface(version: String): ModuleID = "com.github.sbt.junit" % "jupiter-interface" % version
  def junitJupiterParams(version: String): ModuleID = "org.junit.jupiter" % "junit-jupiter-params" % version
  def junitVintageEngine(version: String): ModuleID = "org.junit.vintage" % "junit-vintage-engine" % version

  val ivy2: ModuleID = "org.apache.ivy" % "ivy" % "2.6.0"

  // Transitive dependencies of scalastyle. The versions are deliberately outdated, to keep compatibility with scalastyle.
  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  val scalaCollectionCompat: ModuleID = "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
  val scalastyle: ModuleID = "com.beautiful-scala" %% "scalastyle" % "1.5.1"

  // We exclude "coursier interface" because we depend on an up-to-date version below.
  val scalafmtDynamic = "org.scalameta" %% "scalafmt-dynamic" % "3.7.17" exclude("io.get-coursier", "interface")
  val scalaMetaCore: ModuleID = "org.scalameta" %% "scalameta" % "4.5.13" excludeAll(
    ExclusionRule(organization = "com.thesamet.scalapb"),
    ExclusionRule(organization = "org.scala-lang")
  )
  val scalapbRuntime: ModuleID = "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.11" % Test exclude("com.google.protobuf", "protobuf-java") // A dependency of scalameta, only used in tests.

  val scalaTestNotSpecified: ModuleID = "org.scalatest" %% "scalatest" % "3.2.19"

  // has to be in the compiler process classpath along with spray-json
  // when updating the version, do not forget to:
  //  1. update version in the sbt-idea-compiler indices plugin too
  //  2. update version in scala-plugin-common.xml compilerServer.plugin classpath setting
  // Do not use %% to determine the cross-version for spray-json. The dependency is applied to a few modules
  // which currently have a different Scala version and the build definition fails.
  // cross CrossVersion.for3use2_13 also cannot be used because it is not compatible with packageLibraryMappings.
  val sprayJson: ModuleID = "io.spray" % "spray-json_2.13" % Versions.sprayJsonVersion
  val compilerIndicesProtocol: ModuleID = "org.jetbrains.scala" %% "scala-compiler-indices-protocol" % compilerIndicesVersion

  val nailgun = "org.jetbrains" % "nailgun-server-for-scala-plugin" % nailgunVersion

  val zinc = "org.scala-sbt" %% "zinc" % zincVersion excludeAll ExclusionRule(organization = "org.apache.logging.log4j")
  val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  val sbtInterface = "org.scala-sbt" % "util-interface" % sbtVersion

  // "provided" danger: we statically depend on a single version, but need to support all the version
  // some part of our code is now statically dependent on lib classes, another part uses reflections for other versions
  object provided {
    val scalaTest = scalaTestNotSpecified % Provided
    val utest = "com.lihaoyi" %% "utest" % "0.9.5" % Provided
    val specs2_2x = "org.specs2" % "specs2-core_2.12" % "2.5" % Provided excludeAll ExclusionRule(organization = "org.ow2.asm")
    val specs2_4x = "org.specs2" %% "specs2-core" % "4.18.0" % Provided excludeAll ExclusionRule(organization = "org.ow2.asm")
  }

  /** The filtering function returns true for jars to be removed.
   * Its purpose is to exclude platform jars that may conflict with plugin dependencies. */
  val excludeJarsFromPlatformDependencies: File => Boolean = { file =>
    val fileName = file.getName
    // we explicitly specify dependency on the jetbrains annotations library, see SCL-20557
    fileName == "annotations.jar" ||
      // We explicitly specify dependency on JUnit 4 library.
      // See also https://youtrack.jetbrains.com/issue/IDEA-315065/The-IDE-runtime-classpath-contains-conflicting-JUnit-classes-from-lib-junit.jar-vs-lib-junit4.jar#focus=Comments-27-6987325.0-0
      fileName == "junit4.jar"
  }

  val intellijTestFrameworkCore: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-core" % IntellijTestFrameworkVersion).notTransitive()
  val intellijTestFrameworkCommon: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-common" % IntellijTestFrameworkVersion).notTransitive()
  val intellijTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework" % IntellijTestFrameworkVersion).notTransitive()
  val intellijJUnit5TestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-junit5" % IntellijTestFrameworkVersion).notTransitive()
  val intellijJUnit5CodeInsightTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-junit5-code-insight" % IntellijTestFrameworkVersion).notTransitive()
  val intellijJUnit5EelTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-junit5-eel" % IntellijTestFrameworkVersion).notTransitive()
  val intellijJavaTestFrameworkShared: ModuleID = ("com.jetbrains.intellij.java" % "java-test-framework-shared" % IntellijTestFrameworkVersion).notTransitive()
  val intellijJavaTestFrameworkBackend: ModuleID = ("com.jetbrains.intellij.java" % "java-test-framework-backend" % IntellijTestFrameworkVersion).notTransitive()
  val intellijJavaTestFramework: ModuleID = ("com.jetbrains.intellij.java" % "java-test-framework" % IntellijTestFrameworkVersion).notTransitive()
  val intellijDebuggerTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "debugger-test-framework" % IntellijTestFrameworkVersion).notTransitive()
  val intellijMavenTestFramework: ModuleID = ("com.jetbrains.intellij.maven" % "maven-test-framework" % IntellijTestFrameworkVersion).notTransitive()
  val intellijEelJavaTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-eel-java" % IntellijTestFrameworkVersion).notTransitive()
  val intellijExternalSystemTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "external-system-test-framework" % IntellijTestFrameworkVersion).notTransitive()

  // The uast-test-framework can currently only be resolved from the SNAPSHOTS repository. It is not being published for every intellijVersion yet.
  // This will hopefully change soon.
  val intellijUastTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "uast-test-framework" % IntellijTestFrameworkVersion).notTransitive()

  val intellijTeamCityTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "test-framework-team-city" % IntellijTestFrameworkVersion).notTransitive()
  val slf4jApi: ModuleID = "org.slf4j" % "slf4j-api" % "2.0.18" // Necessary as a test dependency for the "external-system-test-framework".
  val intellijIdeMetricsBenchmark: ModuleID = ("com.jetbrains.intellij.tools" % "ide-metrics-benchmark" % IntellijTestFrameworkVersion).notTransitive()
  val intellijIdeMetricsCollector: ModuleID = ("com.jetbrains.intellij.tools" % "ide-metrics-collector" % IntellijTestFrameworkVersion).notTransitive()
  val intellijIdeUtilCommon: ModuleID = ("com.jetbrains.intellij.tools" % "ide-util-common" % IntellijTestFrameworkVersion).notTransitive.notTransitive()

  val coursierApi = "io.get-coursier" % "interface" % "1.0.28" excludeAll ExclusionRule(organization = "org.slf4j")
}

object DependencyGroups {
  import Dependencies.*
  import Versions.*

  val scalaCommunity: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
    scalaParserCombinators,
    coursierPaths,
    ivy2,
    compilerIndicesProtocol,
    scalafmtDynamic
  )

  //These libraries are already included in IDEA platform
  private val bspExclusions: Seq[InclusionRule] = Seq(
    ExclusionRule("com.google.code.gson", "gson"),
    ExclusionRule("com.google.guava", "guava"),
    //NOTE: lsp4j is present in IDEA Ultimate jars: it's bundled into app.jar (NOTE: it has a higher version) then bsp4j uses
    //but it's not available in IDEA Community
    //So we can't simply exclude this library from the dependencies
    //ExclusionRule("org.eclipse.lsp4j", "org.eclipse.lsp4j.jsonrpc")
  )

  // Exclude bsp4j from the bloop-rifle library to prevent it from evicting the currently used bsp4j specified by bspVersion
  val bloopRifleExclusions: Seq[InclusionRule] = bspExclusions :+ ExclusionRule("ch.epfl.scala", "bsp4j")
  val bspTestkitExclusions: Seq[InclusionRule] = bspExclusions :+ ExclusionRule("org.scalacheck", "scalacheck_2.13")
  val bsp: Seq[ModuleID] = Seq(
    // bloop-rifle and bsp-testkit libraries are not published for Scala 3
    ("ch.epfl.scala" % "bloop-rifle_2.13" % bloopVersion).excludeAll(bloopRifleExclusions *),
    ("ch.epfl.scala" % "bsp4j" % bspVersion).excludeAll(bspExclusions *),
    ("ch.epfl.scala" % "bsp-testkit_2.13" % bspVersion).excludeAll(bspTestkitExclusions *) % Test,
    "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
  )

  val decompiler: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect
  )

  val testRunners: Seq[ModuleID] = Seq(
    provided.scalaTest,
    provided.utest,
    provided.specs2_4x
  )
}

object DependencyResolvers {
  val IntelliJDependencies = "IntelliJ Dependencies" at "https://cache-redirector.jetbrains.com/intellij-dependencies"
  val PackageSearch = "Package Search" at "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/kpm/public"
  val Grazie = "Grazie" at "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/grazi/grazie-platform-public"
}
