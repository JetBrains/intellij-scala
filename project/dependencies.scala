import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.idea.IntellijVersionUtils
import sbt._

object Versions {
  val scalaVersion: String = "2.13.10"
  val scala3Version: String = "3.2.2"

  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  // NOTE: sbt-launch / bloop-launcher won't be fetched on refresh.
  // run runtimeDependencies/update manually
  val sbtVersion: String = Sbt.latest
  val bloopVersion = "1.5.4"
  val zincVersion = "1.8.0"
  val intellijVersion = "231.8109.144"

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
  val sbtStructureVersion: String = "2023.1.0"
  val sbtIdeaShellVersion: String = "2021.1.0"
  val compilerIndicesVersion = "1.0.13"

  object Sbt {
    val binary_0_13 = "0.13"
    val binary_1_0 = "1.0" // 1.0 is the binary version of sbt 1.x series

    val latest_0_13 = "0.13.18"
    val latest_1_0 = "1.8.2"
    val latest: String = latest_1_0
    // ATTENTION: after adding sbt major version, also update:
    // buildInfoKeys, Sbt.scala and SbtUtil.latestCompatibleVersion

    def scalaVersion(v: String): String =
      if (v.startsWith(Sbt.binary_0_13)) "2.10"
      else if (v.startsWith(Sbt.binary_1_0)) "2.12"
      else throw new RuntimeException(s"Unknown sbt binary version: $v -- need to update dependencies.scala?")
  }
}

object Dependencies {

  import Versions._

  val sbtLaunch: ModuleID = "org.scala-sbt" % "sbt-launch" % sbtVersion intransitive()
  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
  val scalaParallelCollections: ModuleID = "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
  val scalaCollectionContrib: ModuleID = "org.scala-lang.modules" %% "scala-collection-contrib" % "0.3.0"
  //  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains.scala" %% "sbt-structure-core" % sbtStructureVersion
  val evoInflector: ModuleID = "org.atteo" % "evo-inflector" % "1.3"
  val directories: ModuleID = "dev.dirs" % "directories" % "26"
  // NOTE: current latest version is in https://github.com/unkarjedy/scalatest-finders.git repository

  val commonsLang: ModuleID = "org.apache.commons" % "commons-lang3" % "3.12.0"

  val jetbrainsAnnotations: ModuleID = "org.jetbrains" % "annotations" % "23.1.0"

  //NOTE: JUnit 4 dependency is already available via intellij main jars.
  // It's bundled together with it's transitive dependencies in single junit4.jar (in <sdk_root>/lib folder)
  // HOWEVER junit4.jar it is excluded via excludeJarsFromPlatformDependencies
  // We explicitly include junit dependency in all modules.
  // This is done because some modules are not intellij-based and they explicitly define junit dependency anyway.
  // Due to imperfection of classpath construction in the end there might be multiple junit4 jrs in classpath.
  // (Both runtime and compilation time)
  val junit: ModuleID = "junit" % "junit" % junitVersion
  val junitInterface: ModuleID = "com.github.sbt" % "junit-interface" % junitInterfaceVersion

  val ivy2: ModuleID = "org.apache.ivy" % "ivy" % "2.5.1"

  val scalastyle: ModuleID = "com.beautiful-scala" %% "scalastyle" % "1.5.1"

  val scalafmtDynamic = "org.scalameta" %% "scalafmt-dynamic" % "3.7.1"
  val scalaMetaCore: ModuleID = "org.scalameta" %% "scalameta" % "4.5.13" withSources() exclude("com.google.protobuf", "protobuf-java")
  val fastparse: ModuleID = "com.lihaoyi" %% "fastparse" % "2.3.3" // transitive dependency of scalaMeta, needs explicit versioning

  val scalaTestNotSpecified: ModuleID = "org.scalatest" %% "scalatest" % "3.2.15"
  val scalaTest: ModuleID = scalaTestNotSpecified % Test
  val scalaCheck: ModuleID = "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test

  // has to be in the compiler process classpath along with spray-json
  // when updating the version, do not forget to:
  //  1. update version in the sbt-idea-compiler indices plugin too
  //  2. update version in scala-plugin-common.xml compilerServer.plugin classpath setting
  val compilerIndicesProtocol: ModuleID = "org.jetbrains.scala" %% "scala-compiler-indices-protocol" % compilerIndicesVersion

  val nailgun = "org.jetbrains" % "nailgun-server-for-scala-plugin" % "1.3.0"

  val zinc = "org.scala-sbt" %% "zinc" % zincVersion excludeAll ExclusionRule(organization = "org.apache.logging.log4j")
  /** actually this is is compilerInterface (TODO: rename, cause naming difference is misleading) */
  val zincInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  val sbtInterface = "org.scala-sbt" % "util-interface" % sbtVersion

  val compilerBridgeSources_2_10 = "org.scala-sbt" % "compiler-bridge_2.10" % zincVersion classifier "sources"
  val compilerBridgeSources_2_11 = "org.scala-sbt" % "compiler-bridge_2.11" % zincVersion classifier "sources"
  val compilerBridgeSources_2_13 = "org.scala-sbt" % "compiler-bridge_2.13" % zincVersion classifier "sources"
  val sbtBridge_Scala_3_0 = "org.scala-lang" % "scala3-sbt-bridge" % "3.0.2"
  val sbtBridge_Scala_3_1 = "org.scala-lang" % "scala3-sbt-bridge" % "3.1.3"
  val sbtBridge_Scala_3_2 = "org.scala-lang" % "scala3-sbt-bridge" % "3.2.2"
  val sbtBridge_Scala_3_3 = "org.scala-lang" % "scala3-sbt-bridge" % "3.3.1-RC1-bin-20230206-21729d2-NIGHTLY"

  val java9rtExport = "org.scala-sbt.rt" % "java9-rt-export" % "0.1.0"

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

  private def sbtPluginDependency(module: ModuleID, sbtVersion: String): ModuleID =
    sbt.Defaults.sbtPluginExtra(module, sbtVersion, Sbt.scalaVersion(sbtVersion))

}

object DependencyGroups {
  import Dependencies._
  import Versions._

  val scalaCommunity: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
    scalaMetaCore,
    fastparse % Test, //used in single test org.jetbrains.plugins.scala.annotator.TreeTest
    //    scalaParserCombinators,
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
    ("ch.epfl.scala" % "bsp4j" % bspVersion).excludeAll(bspExclusions: _*),
    ("ch.epfl.scala" %% "bsp-testkit" % bspVersion).excludeAll(bspExclusions: _*) % Test,
    scalaTest,
    scalaCheck,
    "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % Test,
    "com.propensive" %% "mercator" % "0.3.0"
  )

  val dfa: Seq[ModuleID] = Seq(
    scalaTest
  )

  val traceLogger: Seq[ModuleID] = Seq(
    "com.lihaoyi" %% "upickle" % "2.0.0"
  )

  val decompiler: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    commonsLang
  )

  val testRunners: Seq[ModuleID] = Seq(
    provided.scalaTest,
    provided.utest,
    provided.specs2_4x
  )

  val runtime: Seq[ModuleID] = Seq(
    sbtLaunch,
    sbtInterface,
    compilerBridgeSources_2_10,
    compilerBridgeSources_2_11,
    compilerBridgeSources_2_13,
    sbtBridge_Scala_3_0,
    java9rtExport
  )

  // workaround for https://github.com/JetBrains/sbt-idea-plugin/issues/110
  val runtime2: Seq[ModuleID] = Seq(
    sbtBridge_Scala_3_1,
  )

  val runtime3: Seq[ModuleID] = Seq(
    sbtBridge_Scala_3_2
  )

  val runtime4: Seq[ModuleID] = Seq(
    sbtBridge_Scala_3_3
  )
}