import sbt._

object Versions {
  val scalaVersion: String = Scala.project
  val scalaBinaryVersion: String = Scala.binary_2_12
  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  val sbtVersion: String = Sbt.latest
  val zincVersion = "1.1.1"
  val ideaVersion = "191.5109.14"
  val sbtStructureVersion: String = "2018.2.1+4-88400d3f"
  val sbtIdeaShellVersion: String = "2018.3"
  val scalaMetaVersion = "1.8.0"
  val paradiseVersion = "3.0.0-M11"
  val scalazVersion = "7.1.0"

  object Scala {
    val binary_2_9 = "2.9.2"
    val binary_2_10 = "2.10"
    val binary_2_11 = "2.11"
    val binary_2_12 = "2.12"
    val binary_2_13 = "2.13"

    // ATTENTION: When changing any of these versions,
    // they currently need to be updated in org.jetbrains.plugins.scala.debugger.ScalaVersion
    val latest_2_9 = "2.9.3"
    val latest_2_10 = "2.10.7"
    val latest_2_11 = "2.11.12"
    val latest_2_12 = "2.12.8"
    val latest_2_13 = "2.13.0-M5"
    val latest: String = latest_2_12
    /** Version used to build this project. Prefer latest_2_12 unless it causes problems. */
    val project = "2.12.7"

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
    val latest_1_0 = "1.2.7"
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
  val jamm: ModuleID = "com.github.jbellis" % "jamm" % "0.3.1"
  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains" %% "sbt-structure-core" % sbtStructureVersion
  val evoInflector: ModuleID = "org.atteo" % "evo-inflector" % "1.2"
  val scalatestFindersPatched: ModuleID = "org.scalatest" % "scalatest-finders-patched" % "0.9.9"

  //  val specs2: ModuleID = "org.specs2" %% "specs2-core" % "3.9.1" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")
  val specs2: ModuleID = "org.specs2" %% "specs2-core" % "2.4.17" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")

  val commonsLang: ModuleID = "commons-lang" % "commons-lang" % "2.6"
  val junitInterface: ModuleID = "com.novocode" % "junit-interface" % "0.11" % "test"
  val ivy2: ModuleID = "org.apache.ivy" % "ivy" % "2.4.0"

  val typesafeConfig: ModuleID = "com.typesafe" % "config" % "1.3.3"

  val scalastyle: ModuleID = "org.scalastyle" %% "scalastyle" % "1.0.0"
  val scalariform: ModuleID = "org.scalariform" %% "scalariform" % "0.2.2"
  val macroParadise: ModuleID = "org.scalameta" % "paradise" % paradiseVersion cross CrossVersion.full
  val scalaMetaCore: ModuleID = "org.scalameta" %% "scalameta" % scalaMetaVersion withSources() exclude("com.google.protobuf", "protobuf-java")
  val fastparse: ModuleID = "com.lihaoyi" % s"fastparse_$scalaBinaryVersion" % "0.4.3" // transitive dependency of scalaMeta, needs explicit versioning

  val bcel: ModuleID = "org.apache.bcel" % "bcel" % "6.0"

  val nailgun: ModuleID = "org.jetbrains" % "nailgun-patched" % "1.0.0"
  val zinc = "org.scala-sbt" %% "zinc" % zincVersion
  val zincInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  val sbtInterface = "org.scala-sbt" % "util-interface" % "1.1.2"

  val compilerBridgeSources_2_10 = "org.scala-sbt" % "compiler-bridge_2.10" % zincVersion classifier "sources"
  val compilerBridgeSources_2_11 = "org.scala-sbt" % "compiler-bridge_2.11" % zincVersion classifier "sources"
  val compilerBridgeSources_2_13 = "org.scala-sbt" % "compiler-bridge_2.13.0-M2" % zincVersion classifier "sources"

  /** The filtering function returns true for jars to be removed.
    * It's purpose is to exclude platform jars that may conflict with plugin dependencies. */
  val excludeJarsFromPlatformDependencies: File => Boolean = { file =>
    file.getName.contains("lsp4j") // version conflict with bsp4j in ultimate platform
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
    scalaParserCombinators,
    sbtStructureCore,
    evoInflector,
    scalatestFindersPatched,
    jamm,
    ivy2,
    typesafeConfig,
    scalastyle,
    scalariform
  )

  val bsp: Seq[ModuleID] = Seq(
    ("org.scala-sbt.ipcsocket" % "ipcsocket" % "1.0.0")
      .exclude("net.java.dev.jna","jna") // included in IDEA platform
      .exclude("net.java.dev.jna","jna-platform") // included in IDEA platform
    ,
    ("ch.epfl.scala" % "bsp4j" % "2.0.0-M2")
      .exclude("com.google.code.gson", "gson") // included in IDEA platform
      .exclude("com.google.guava", "guava") // included in IDEA platform
  )

  val decompiler: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    scalaCompiler,
    commonsLang,
    bcel
  )

  val runners: Seq[ModuleID] = Seq(
    specs2,
    "org.scala-lang" % "scala-compiler" % scalaVersion,
    "org.scalatest" %% "scalatest" % "3.0.1" % "provided",
    "com.lihaoyi" %% "utest" % "0.5.4" % "provided"
  )

  val sbtRuntime: Seq[ModuleID] = Seq(
    sbtLaunch,
    compilerBridgeSources_2_10,
    compilerBridgeSources_2_11,
    compilerBridgeSources_2_13
  )
}
