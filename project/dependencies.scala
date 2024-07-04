import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.idea.IntellijVersionUtils
import sbt.*

object Versions {
  val scalaVersion: String = "2.13.14"
  val scala3Version: String = "3.3.3"

  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  // NOTE: sbt-launch / bloop-launcher won't be fetched on refresh.
  // run runtimeDependencies/update manually
  val sbtVersion: String = Sbt.latest
  val bloopVersion = "1.5.6"
  val zincVersion = "1.10.0"

  // ATTENTION: check the comment in `Common.newProjectWithKotlin` when updating this version
  val intellijVersion = "242.19890.5"

  def isNightlyIntellijVersion: Boolean = intellijVersion.count(_ == '.') == 1

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
  val junitInterfaceVersion: String = "0.13.3"
  val junit5JupiterVersion: String = "5.10.2"

  val bspVersion = "2.1.0-M3"
  val sbtStructureVersion: String = "2024.2.2"
  val sbtIdeaShellVersion: String = "2021.1.0"
  val compilerIndicesVersion = "1.0.14"

  val java9rtExportVersion: String = "0.1.0"

  val scalaExpressionCompiler: String = "3.1.6"

  /**
   * For `"org.languagetool" % "language-*"` dependencies
   *
   * This version should be the same as in `com.intellij.grazie.GraziePlugin.LanguageTool.version` (it's updated automatically by `UpdateVersions` script)
   * Note that in Grazie plugin they actually use custom language tool distributions (see com.intellij.grazie.GraziePlugin.LanguageTool.url)
   * However according to Peter Gromov it shouldn't be important for us and we can use maven dependencies.
   * Those custom distributions usually contain performance fixes and not the logic.
   */
  val LanguageToolVersion = "6.4"

  object Sbt {
    val binary_0_13 = "0.13"
    val binary_1_0 = "1.0" // 1.0 is the binary version of sbt 1.x series

    //sbt-structure-extractor is cross-published in a non-standard way,
    //against multiple 1.x versions so it uses an exact binary version 1.x.
    //Versions 1.0-1.2 use 1.2, versions 1.3 and above use 1.3
    val structure_extractor_binary_1_2 = "1.2"
    val structure_extractor_binary_1_3 = "1.3"

    val latest_0_13 = "0.13.18"
    val latest_1_0 = "1.10.0"
    val latest: String = latest_1_0
    // ATTENTION: after adding sbt major version, also update:
    // buildInfoKeys, Sbt.scala and SbtUtil.latestCompatibleVersion
  }
}

object Dependencies {

  import Versions.*

  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scala3Library: ModuleID = "org.scala-lang" % "scala3-library_3" % scala3Version
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
  val tastyCore: ModuleID = "org.scala-lang" % "tasty-core_3" % Versions.scala3Version
  val scalaParallelCollections: ModuleID = "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
  //  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains.scala" %% "sbt-structure-core" % sbtStructureVersion
  val evoInflector: ModuleID = "org.atteo" % "evo-inflector" % "1.3"
  val directories: ModuleID = "dev.dirs" % "directories" % "26"
  val apacheCommonsText: ModuleID = "org.apache.commons" % "commons-text" % "1.11.0"
  // NOTE: current latest version is in https://github.com/unkarjedy/scalatest-finders.git repository

  val jetbrainsAnnotations: ModuleID = "org.jetbrains" % "annotations" % "24.1.0"

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
  val junitInterface: ModuleID = "com.github.sbt" % "junit-interface" % junitInterfaceVersion

  /**
   * IntelliJ test framework classes depend a lot on JUnit 5 classes.<br>
   * To avoid compilation errors, we need to add it as well.<br>
   * We add just "junit-jupiter-api" instead of "junit-jupiter" to keep it minimalistic.<br>
   * In principle, we can start depending on directly "junit-jupiter" when needed.
   */
  val junit5JupiterApi: ModuleID = "org.junit.jupiter" % "junit-jupiter-api" % junit5JupiterVersion

  private val junit5JupiterEngine: ModuleID = "org.junit.jupiter" % "junit-jupiter-engine" % junit5JupiterVersion
  private val junit5JupiterVintageEngine: ModuleID = "org.junit.vintage" % "junit-vintage-engine" % junit5JupiterVersion
  private val junitPlatformLauncher: ModuleID = "org.junit.platform" % "junit-platform-launcher" % "1.10.2"

  /**
   * These dependencies are needed for IntelliJ to run correctly run tests using JUnit 5 runner.<br>
   * Actually, IntelliJ adds these dependencies to the test classpath automatically in<br>
   * `com.intellij.execution.junit.TestObject.appendJUnit5LauncherClasses`<br>
   * However, when running tests via Run Configuration we pass the classpath via the `--classpath` VM option.
   * (it's defined by sbt-idea-plugin).<br>
   * As a result, the class path provided by intellij is ignored. So we need to add the dependencies manually.
   *
   * ===Note about JUnit 5 test runner===
   * After we added dependency on JUnit 5 artifact, IntelliJ will sometimes prefer using JUnit 5 test runner
   * (`com.intellij.junit5.JUnit5IdeaTestRunner`). For example, when we use a pattern to find tests.
   * To be able to run JUnit 4 tests "junit-vintage-engine" is needed
   *
   * @see `com.intellij.execution.junit.TestObject.getRunnerInner`
   * @see `com.intellij.execution.junit.JUnitUtil.isJUnit5`
   */
  val junit5EngineTestDependencies: Seq[ModuleID] = Seq(
    junit5JupiterEngine,
    junit5JupiterVintageEngine,
    junitPlatformLauncher,
  ).map(_ % Test)

  val ivy2: ModuleID = "org.apache.ivy" % "ivy" % "2.5.2"

  val scalastyle: ModuleID = "com.beautiful-scala" %% "scalastyle" % "1.5.1"

  // "io.get-coursier" % "interface" is a large jar (over 3 megabytes) that is packaged as a whole application.
  // It shades and packages all of its transitive dependencies in the jar, including the Scala library.
  // "scalafmt-dynamic" uses "interface" to resolve and download new versions of scalafmt using Coursier, as the user
  // updates their ".scalafmt.conf" configuration file. In the Scala Plugin for IntelliJ IDEA, we have our own
  // resolution and download mechanism based on ivy. We do not need a dependency on Coursier interface.
  val scalafmtDynamic = "org.scalameta" %% "scalafmt-dynamic" % "3.7.17" exclude("io.get-coursier", "interface")
  val scalaMetaCore: ModuleID = "org.scalameta" %% "scalameta" % "4.5.13" excludeAll(
    ExclusionRule(organization = "com.thesamet.scalapb"),
    ExclusionRule(organization = "org.scala-lang")
  )
  val scalapbRuntime: ModuleID = "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.11" % Test exclude("com.google.protobuf", "protobuf-java") // A dependency of scalameta, only used in tests.

  val scalaTestNotSpecified: ModuleID = "org.scalatest" %% "scalatest" % "3.2.17"
  val scalaTest: ModuleID = scalaTestNotSpecified % Test
  val scalaCheck: ModuleID = "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test

  // has to be in the compiler process classpath along with spray-json
  // when updating the version, do not forget to:
  //  1. update version in the sbt-idea-compiler indices plugin too
  //  2. update version in scala-plugin-common.xml compilerServer.plugin classpath setting
  val compilerIndicesProtocol: ModuleID = "org.jetbrains.scala" %% "scala-compiler-indices-protocol" % compilerIndicesVersion

  val nailgun = "org.jetbrains" % "nailgun-server-for-scala-plugin" % "1.3.1"

  val zinc = "org.scala-sbt" %% "zinc" % zincVersion excludeAll ExclusionRule(organization = "org.apache.logging.log4j")
  val compilerInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  val sbtInterface = "org.scala-sbt" % "util-interface" % sbtVersion

  // "provided" danger: we statically depend on a single version, but need to support all the version
  // some part of our code is now statically dependent on lib classes, another part uses reflections for other versions
  object provided {
    val scalaTest = scalaTestNotSpecified % Provided
    val utest = "com.lihaoyi" %% "utest" % "0.8.1" % Provided
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

  val intellijMavenTestFramework: ModuleID = ("com.jetbrains.intellij.maven" % "maven-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies).notTransitive()
  val intellijExternalSystemTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "external-system-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies).notTransitive()
  val intellijIdeMetricsBenchmark: ModuleID = ("com.jetbrains.intellij.tools" % "ide-metrics-benchmark" % Versions.intellijVersion_ForManagedIntellijDependencies).notTransitive()
  val intellijIdeUtilCommon: ModuleID = ("com.jetbrains.intellij.tools" % "ide-util-common" % Versions.intellijVersion_ForManagedIntellijDependencies).notTransitive.notTransitive()

  val packageSearchClientJvm = ("org.jetbrains.packagesearch" % "packagesearch-api-client-jvm" % "3.0.0").excludeAll(
    ExclusionRule(organization = "ch.qos.logback"),
    ExclusionRule(organization = "com.soywiz.korlibs.krypto"),
    ExclusionRule(organization = "io.ktor", name = "ktor-client-content-negotiation-jvm"),
    ExclusionRule(organization = "io.ktor", name = "ktor-client-encoding-jvm"),
    ExclusionRule(organization = "io.ktor", name = "ktor-http-jvm"),
    ExclusionRule(organization = "io.ktor", name = "ktor-serialization-kotlinx-json-jvm"),
    ExclusionRule(organization = "io.ktor", name = "ktor-serialization-kotlinx-jvm"),
    ExclusionRule(organization = "org.jetbrains.kotlin"),
    ExclusionRule(organization = "org.jetbrains.kotlinx"),
    ExclusionRule(organization = "org.slf4j"),
  )
}

object DependencyGroups {
  import Dependencies.*
  import Versions.*

  val scalaCommunity: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
    scalaMetaCore,
    scalapbRuntime,
    sbtStructureCore,
    evoInflector,
    directories,
    ivy2,
    scalastyle,
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

  val bsp: Seq[ModuleID] = Seq(
    ("ch.epfl.scala" % "bsp4j" % bspVersion).excludeAll(bspExclusions *),
    ("ch.epfl.scala" %% "bsp-testkit" % bspVersion).excludeAll(bspExclusions *) % Test,
    scalaTest,
    scalaCheck
  )

  val decompiler: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    apacheCommonsText
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
}
