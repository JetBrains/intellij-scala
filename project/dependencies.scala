import sbt._

object Versions {
  val scalaVersion: String = "2.13.2"
  val scalaBinaryVersion: String = Scala.binary_2_12 // TODO 2_13?
  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  // NOTE: sbt-launch / bloop-launcher won't be fetched on refresh.
  // run runtimeDependencies/update manually
  val sbtVersion: String = Sbt.latest
  val bloopVersion = "1.4.5"
  val zincVersion = "1.4.0-M12"
  val intellijVersion = "211.6693.14"
  val bspVersion = "2.0.0-M12+27-4994bd9d-SNAPSHOT"
  val sbtStructureVersion: String = "2020.3"
  val sbtIdeaShellVersion: String = "2018.3"
  val compilerIndicesVersion = "1.0.8"
  val scalaMetaVersion = "4.3.12"
  val paradiseVersion = "3.0.0-M11"

  object Scala {
    val binary_2_9 = "2.9.2"
    val binary_2_10 = "2.10"
    val binary_2_11 = "2.11"
    val binary_2_12 = "2.12"
    val binary_2_13 = "2.13"

    def binaryVersion(v: String): String =
      if (v.startsWith("2.9")) binary_2_9
      else if (v.startsWith(binary_2_10)) binary_2_10
      else if (v.startsWith(binary_2_11)) binary_2_11
      else if (v.startsWith(binary_2_12)) binary_2_12
      else if (v.startsWith(binary_2_13)) binary_2_13
      else throw new RuntimeException(s"Unknown Scala binary version: $v -- need to update dependencies.scala?")
  }

  object Sbt {
    val binary_0_12 = "0.12"
    val binary_0_13 = "0.13"
    val binary_1_0 = "1.0" // 1.0 is the binary version of sbt 1.x series

    val latest_0_12 = "0.12.4"
    val latest_0_13 = "0.13.18"
    val latest_1_0 = "1.4.3"
    val latest: String = latest_1_0
    // ATTENTION: after adding sbt major version, also update:
    // buildInfoKeys, Sbt.scala and SbtUtil.latestCompatibleVersion

    def scalaVersion(v: String): String =
      if (v.startsWith(Sbt.binary_0_12)) Scala.binary_2_9
      else if (v.startsWith(Sbt.binary_0_13)) Scala.binary_2_10
      else if (v.startsWith(Sbt.binary_1_0)) Scala.binary_2_12
      else throw new RuntimeException(s"Unknown sbt binary version: $v -- need to update dependencies.scala?")
  }
}

object Dependencies {

  import Versions._

  val sbtStructureExtractor: ModuleID = "org.jetbrains" % "sbt-structure-extractor" % sbtStructureVersion
  val sbtStructureExtractor_013: ModuleID = sbtPluginDependency(sbtStructureExtractor, Sbt.binary_0_13)
  val sbtStructureExtractor_100: ModuleID = sbtPluginDependency(sbtStructureExtractor, Sbt.binary_1_0)

  val sbtLaunch: ModuleID = "org.scala-sbt" % "sbt-launch" % sbtVersion intransitive()
//  val bloopLauncher: ModuleID = "ch.epfl.scala" % s"bloop-launcher_$scalaBinaryVersion" % bloopVersion
  val jamm: ModuleID = "com.github.jbellis" % "jamm" % "0.3.1"
  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
  val scalaParallelCollections: ModuleID = "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
//  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains" %% "sbt-structure-core" % sbtStructureVersion
  val evoInflector: ModuleID = "org.atteo" % "evo-inflector" % "1.2"
  // NOTE: current latest version is in https://github.com/unkarjedy/scalatest-finders.git repository

  val commonsLang: ModuleID = "commons-lang" % "commons-lang" % "2.6"
  val junitInterface: ModuleID = "com.novocode" % "junit-interface" % "0.11" % "test"
  val ivy2: ModuleID = "org.apache.ivy" % "ivy" % "2.4.0"

  val scalastyle: ModuleID = "com.beautiful-scala" %% "scalastyle" % "1.4.0"
  val scalariform: ModuleID = "org.scalariform" %% "scalariform" % "0.2.10"
  val scalafmt: Seq[ModuleID] = Seq(
    "com.geirsson" %% "scalafmt-core" % "1.5.1",
    "com.geirsson" %% "metaconfig-core" % "0.4.0",
    "com.geirsson" %% "metaconfig-typesafe-config" % "0.4.0",
    "com.typesafe" % "config" % "1.2.1",
    "com.lihaoyi" %% "sourcecode" % "0.1.3"
  )
//  val macroParadise: ModuleID = "org.scalameta" % "paradise" % paradiseVersion cross CrossVersion.full
  val scalaMetaCore: ModuleID = "org.scalameta" %% "scalameta" % scalaMetaVersion withSources() exclude("com.google.protobuf", "protobuf-java")
  val fastparse: ModuleID = "com.lihaoyi" %% s"fastparse" % "2.3.0" // transitive dependency of scalaMeta, needs explicit versioning

  val scalaTestNotSpecified: ModuleID = "org.scalatest" %% "scalatest" % "3.2.0"
  val scalaTest: ModuleID = scalaTestNotSpecified % "test"
  val scalaCheck: ModuleID = "org.scalatestplus" %% "scalacheck-1-14" % "3.2.1.0" % "test"

  val bcel: ModuleID = "org.apache.bcel" % "bcel" % "6.0"

  // has to be in the compiler process classpath along with spray-json
  // when updating the version, do not forget to:
  //  1. update version in the sbt-idea-compiler indices plugin too
  //  2. update version in scala-plugin-common.xml compilerServer.plugin classpath setting
  val compilerIndicesProtocol: ModuleID = "org.jetbrains.scala" %% "scala-compiler-indices-protocol" % compilerIndicesVersion

  val nailgun: ModuleID = "org.jetbrains" % "nailgun-patched" % "1.0.1"
  val zinc = "org.scala-sbt" %% "zinc" % zincVersion
  val zincInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  val sbtInterface = "org.scala-sbt" % "util-interface" % "1.3.0"

  val compilerBridgeSources_2_10 = "org.scala-sbt" % "compiler-bridge_2.10" % zincVersion classifier "sources"
  val compilerBridgeSources_2_11 = "org.scala-sbt" % "compiler-bridge_2.11" % zincVersion classifier "sources"
  val compilerBridgeSources_2_13 = "org.scala-sbt" % "compiler-bridge_2.13" % zincVersion classifier "sources"
  val dottySbtBridge = "ch.epfl.lamp" % "dotty-sbt-bridge" % "0.27.0-RC1"
  val scala3SbtBridge = "org.scala-lang" % "scala3-sbt-bridge" % "3.0.0-M2"

  // "provided" danger: we statically depend on a single version, but need to support all the version
  // some part of our code is now statically dependent on lib classes, another part uses reflections for other versions
  object provided {
    val scalaTest = scalaTestNotSpecified % "provided"
    val utest = "com.lihaoyi" %% "utest" % "0.7.4" % "provided"
    val specs2_2x = "org.specs2" % "specs2-core_2.12" % "2.4.17" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")
    val specs2_4x = "org.specs2" %% "specs2-core" % "4.8.3" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")
  }

  /** The filtering function returns true for jars to be removed.
   * It's purpose is to exclude platform jars that may conflict with plugin dependencies. */
  val excludeJarsFromPlatformDependencies: File => Boolean = { file =>
    file.getName.contains("lsp4j") // version conflict with lsp4j in ultimate platform
  }

  private def sbtPluginDependency(module: ModuleID, sbtVersion: String): ModuleID =
    sbt.Defaults.sbtPluginExtra(module, sbtVersion, Sbt.scalaVersion(sbtVersion))

}

object DependencyGroups {
  import Dependencies._
  import Versions._

  val sbtBundled: Seq[ModuleID] = Seq(
    zinc,
    zincInterface
  )

  val scalaCommunity: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
    scalaMetaCore,
//    scalaParserCombinators,
    sbtStructureCore,
    evoInflector,
    "io.github.soc" % "directories" % "11",
    jamm,
    ivy2,
    scalastyle,
    scalariform,
    compilerIndicesProtocol
  )

  val bsp: Seq[ModuleID] = Seq(
    ("ch.epfl.scala" % "bsp4j" % bspVersion)
      .exclude("com.google.code.gson", "gson") // included in IDEA platform
      .exclude("com.google.guava", "guava") // included in IDEA platform
    ,
    "ch.epfl.scala" %% "bsp-testkit" % bspVersion % "test",
    scalaTest,
    scalaCheck,
    "org.scalatestplus" %% "junit-4-12" % "3.1.2.0" % "test",
    "com.propensive" %% "mercator" % "0.3.0"
  )

  val dfa: Seq[ModuleID] = Seq(
    scalaTest
  )

  val decompiler: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    commonsLang,
    bcel
  )

  val testRunners: Seq[ModuleID] = Seq(
    provided.scalaTest,
    provided.utest,
    provided.specs2_4x
  )

  val runtime: Seq[ModuleID] = Seq(
//    bloopLauncher,
    sbtLaunch,
    compilerBridgeSources_2_10,
    compilerBridgeSources_2_11,
    compilerBridgeSources_2_13,
    dottySbtBridge,
    scala3SbtBridge,
  )
}
