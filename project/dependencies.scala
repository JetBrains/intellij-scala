import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.idea.IntellijVersionUtils
import sbt.*

object Versions {
  val scalaVersion: String = "2.13.12"
  val scala3Version: String = "3.3.1"

  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  // NOTE: sbt-launch / bloop-launcher won't be fetched on refresh.
  // run runtimeDependencies/update manually
  val sbtVersion: String = Sbt.latest
  val bloopVersion = "1.5.6"
  val zincVersion = "1.9.5"

  // ATTENTION: check the comment in `Common.newProjectWithKotlin` when updating this version
  val intellijVersion = "241.14494.17"

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

  val bspVersion = "2.1.0-M3"
  val sbtStructureVersion: String = "2024.1.1"
  val sbtIdeaShellVersion: String = "2021.1.0"
  val compilerIndicesVersion = "1.0.14"

  val java9rtExportVersion: String = "0.1.0"

  val scalaExpressionCompiler: String = "3.1.6"

  object Sbt {
    val binary_0_13 = "0.13"
    val binary_1_0 = "1.0" // 1.0 is the binary version of sbt 1.x series

    //sbt-structure-extractor is cross-published in a non-standard way,
    //against multiple 1.x versions so it uses an exact binary version 1.x.
    //Versions 1.0-1.2 use 1.2, versions 1.3 and above use 1.3
    val structure_extractor_binary_1_2 = "1.2"
    val structure_extractor_binary_1_3 = "1.3"

    val latest_0_13 = "0.13.18"
    val latest_1_0 = "1.9.7"
    val latest: String = latest_1_0
    // ATTENTION: after adding sbt major version, also update:
    // buildInfoKeys, Sbt.scala and SbtUtil.latestCompatibleVersion
  }
}

object Dependencies {

  import Versions.*

  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "2.2.0"
  val scalaParallelCollections: ModuleID = "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
  //  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains.scala" %% "sbt-structure-core" % sbtStructureVersion
  val evoInflector: ModuleID = "org.atteo" % "evo-inflector" % "1.3"
  val directories: ModuleID = "dev.dirs" % "directories" % "26"
  // NOTE: current latest version is in https://github.com/unkarjedy/scalatest-finders.git repository

  val jetbrainsAnnotations: ModuleID = "org.jetbrains" % "annotations" % "24.1.0"

  //NOTE: JUnit 4 dependency is already available via intellij main jars.
  // It's bundled together with it's transitive dependencies in single junit4.jar (in <sdk_root>/lib folder)
  // HOWEVER junit4.jar it is excluded via excludeJarsFromPlatformDependencies
  // We explicitly include junit dependency in all modules.
  // This is done because some modules are not intellij-based and they explicitly define junit dependency anyway.
  // Due to imperfection of classpath construction in the end there might be multiple junit4 jrs in classpath.
  // (Both runtime and compilation time)
  val junit: ModuleID = "junit" % "junit" % junitVersion
  val junitInterface: ModuleID = "com.github.sbt" % "junit-interface" % junitInterfaceVersion

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
   * It's purpose is to exclude platform jars that may conflict with plugin dependencies. */
  val excludeJarsFromPlatformDependencies: File => Boolean = { file =>
    val fileName = file.getName
    fileName == "annotations.jar" || // we explicitly specify dependency on jetbrains annotations library, see SCL-20557
      fileName == "junit4.jar" // we explicitly specify dependency on junit 4 library
  }

  val intellijMavenTestFramework: ModuleID = ("com.jetbrains.intellij.maven" % "maven-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies).notTransitive()
  val intellijExternalSystemTestFramework: ModuleID = ("com.jetbrains.intellij.platform" % "external-system-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies).notTransitive()
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
