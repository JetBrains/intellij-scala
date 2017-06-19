import sbt._

object Versions {
  val scalaVersion: String = Scala.latest_2_11
  val scalaBinaryVersion: String = Scala.binary_2_11
  val sbtVersion: String = Sbt.latest
  val ideaVersion = "172.2273.8"
  val sbtStructureVersion: String = "7.0.0+45-b2a6b23a"
  val sbtIdeaShellVersion: String = "1.2+2-3eadcace"
  val luceneVersion = "4.8.1"
  val aetherVersion = "1.0.0.v20140518"
  val sisuInjectVersion = "2.2.3"
  val wagonVersion = "2.6"
  val httpComponentsVersion = "4.3.1"
  val scalaMetaVersion = "1.7.0"
  val paradiseVersion = "3.0.0-M8"

  object Scala {
    val binary_2_9 = "2.9.2"
    val binary_2_10 = "2.10"
    val binary_2_11 = "2.11"
    val binary_2_12 = "2.12"

    // ATTENTION: When changing any of these versions,
    // they currently need to be updated in org.jetbrains.plugins.scala.debugger.ScalaVersion
    val latest_2_9 = "2.9.2"
    val latest_2_10 = "2.10.6"
    val latest_2_11 = "2.11.11"
    val latest_2_12 = "2.12.2"
    val latest: String = latest_2_12

    def binaryVersion(v: String): String =
      if (v.startsWith("2.9")) binary_2_9
      else if (v.startsWith(binary_2_10)) binary_2_10
      else if (v.startsWith(binary_2_11)) binary_2_11
      else if (v.startsWith(binary_2_12)) binary_2_12
      else throw new RuntimeException(s"Unknown Scala binary version: $v -- need to update dependencies.scala?")
  }

  object Sbt {
    val binary_0_12 = "0.12"
    val binary_0_13 = "0.13"
    val binary_1_0 = "1.0.0-M6"

    val latest_0_12 = "0.12.4"
    val latest_0_13 = "0.13.15"
    val latest_1_0 = "1.0.0-M6"
    val latest: String = latest_0_13

    def binaryVersion(v: String): String = {
      if (v.startsWith(binary_0_12)) binary_0_12
      else if (v.startsWith(binary_0_13)) binary_0_13
      else if (v.startsWith(binary_1_0)) binary_1_0
      else throw new RuntimeException(s"Unknown sbt binary version: $v -- need to update dependencies.scala?")
    }

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
  val sbtStructureExtractor_012: ModuleID = sbtPluginDependency(sbtStructureExtractor, Sbt.binary_0_12)
  val sbtStructureExtractor_013: ModuleID = sbtPluginDependency(sbtStructureExtractor, Sbt.binary_0_13)
  val sbtStructureExtractor_100: ModuleID = sbtPluginDependency(sbtStructureExtractor, Sbt.binary_1_0)

  val sbtLaunch: ModuleID = "org.scala-sbt" % "sbt-launch" % sbtVersion
  val jamm: ModuleID = "com.github.jbellis" % "jamm" % "0.3.1"
  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
  val scalaParserCombinators: ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
  // this actually needs the explicit version because something in packager breaks otherwise (???)
  val sbtStructureCore: ModuleID = "org.jetbrains" % "sbt-structure-core_2.11" % sbtStructureVersion
  val evoInflector: ModuleID = "org.atteo" % "evo-inflector" % "1.2"
  val scalatestFindersPatched: ModuleID = "org.scalatest" % "scalatest-finders-patched" % "0.9.9"

  val luceneCore: ModuleID = "org.apache.lucene" % "lucene-core" % luceneVersion
  val commonsLang: ModuleID = "commons-lang" % "commons-lang" % "2.6"
  val junitInterface: ModuleID = "com.novocode" % "junit-interface" % "0.11" % "test"

  val scalastyle_2_11: ModuleID = "org.scalastyle" % "scalastyle_2.11" % "0.8.0"
  val scalariform_2_11: ModuleID = "org.scalariform" % "scalariform_2.11" % "0.1.7"
  val macroParadise: ModuleID = "org.scalameta" % "paradise" % paradiseVersion cross CrossVersion.full
  val scalaMetaCore: ModuleID = "org.scalameta" % "scalameta_2.11" % scalaMetaVersion withSources() exclude("com.google.protobuf", "protobuf-java")


  val nailgun: ModuleID = "org.jetbrains" % "nailgun-patched" % "1.0.0"
  val dottyInterface: ModuleID = "ch.epfl.lamp" % "dotty-interfaces" % "0.1.1-20170227-179a5d6-NIGHTLY"
  val compilerInterfaceSources: ModuleID = "org.jetbrains" % "compiler-interface-sources" % "1.0.0"
  val bundledJline: ModuleID = "org.jetbrains" % "jline" % "1.0.0"
  val incrementalCompiler: ModuleID = "org.jetbrains" % "incremental-compiler" % "1.0.0"
  val sbtInterface: ModuleID = "org.jetbrains" % "sbt-interface" % "1.0.0"

  private def sbtPluginDependency(module: ModuleID, sbtVersion: String): ModuleID =
    sbt.Defaults.sbtPluginExtra(module, sbtVersion, Sbt.scalaVersion(sbtVersion))

}

object DependencyGroups {
  import Dependencies._
  import Versions._

  val sbtBundled = Seq(
    compilerInterfaceSources,
    bundledJline,
    incrementalCompiler,
    sbtInterface
  )

  val scalastyle: Seq[ModuleID] = Seq(
    scalastyle_2_11,
    scalariform_2_11
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
    luceneCore
  ) ++ scalastyle

  val scalap = Seq(
    scalaLibrary,
    scalaReflect,
    scalaCompiler,
    commonsLang
  )

  val scalaRunner = Seq(
    "org.specs2" %% "specs2" % "2.3.11" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")
  )

  val runners = Seq(
    "org.specs2" %% "specs2" % "2.3.11" % "provided"  excludeAll ExclusionRule(organization = "org.ow2.asm"),
    "org.scalatest" % "scalatest_2.11" % "2.2.1" % "provided",
    "com.lihaoyi" %% "utest" % "0.3.1" % "provided"
  )

  val sbtLaunchTestDownloader: Seq[ModuleID] =
    Seq("0.12.4", "0.13.0", "0.13.1", "0.13.2",
        "0.13.5", "0.13.6", "0.13.7", "0.13.8",
        "0.13.9", "0.13.11", "0.13.12", "0.13.13",
        "0.13.15")
      .map(v => "org.scala-sbt" % "sbt-launch" % v)

  val testDownloader = Seq(
    "org.scalatest" % "scalatest_2.11" % "2.2.1",
    "org.scalatest" % "scalatest_2.10" % "2.2.1",
    "org.specs2" % "specs2_2.11" % "2.4.15",
    "org.specs2" % "specs2-core_2.11" % "3.0.1",
    "org.specs2" % "specs2-common_2.11" % "3.0.1",
    "org.specs2" % "specs2-matcher_2.11" % "3.0.1",
    "com.lihaoyi" % "utest_2.10" % "0.3.1" % "provided",
    "com.lihaoyi" % "utest_2.11" % "0.4.3" % "provided",
    "com.lihaoyi" % "utest_2.10" % "0.4.3" % "provided",
    "org.scalaz" % "scalaz-core_2.11" % "7.1.0",
    "org.scalaz" % "scalaz-concurrent_2.11" % "7.1.0",
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.5",
    "org.specs2" % "specs2_2.10" % "2.4.6",
    "org.scalaz" % "scalaz-core_2.10" % "7.1.0",
    "org.scalaz" % "scalaz-concurrent_2.10" % "7.1.0",
    "org.scalaz.stream" % "scalaz-stream_2.11" % "0.6a",
    "com.chuusai" % "shapeless_2.11" % "2.0.0",
    "org.typelevel" % "scodec-bits_2.11" % "1.1.0-SNAPSHOT",
    "org.typelevel" % "scodec-core_2.11" % "1.7.0-SNAPSHOT",
    "org.scalatest" % "scalatest_2.11" % "2.1.7",
    "org.scalatest" % "scalatest_2.10" % "2.1.7",
    "org.scalatest" % "scalatest_2.10" % "1.9.2",
    "org.scalatest" % "scalatest_2.11" % "3.0.1",
    "org.scalactic" % "scalactic_2.11" % "3.0.1",
    "com.github.julien-truffaut"  %%  "monocle-core"    % "1.2.0",
    "com.github.julien-truffaut"  %%  "monocle-generic" % "1.2.0",
    "com.github.julien-truffaut"  %%  "monocle-macro"   % "1.2.0",
    "io.spray" %% "spray-routing" % "1.3.1",
    "com.typesafe.slick" %% "slick" % "3.2.0",
    "org.scala-lang.modules" % "scala-async_2.11" % "0.9.5",
    "org.typelevel" %% "cats" % "0.4.0",
    "org.scalameta" % "paradise_2.11.11" % paradiseVersion exclude("org.scalameta", "scalameta_2.11"),
    "org.scalameta" % "scalameta_2.12" % scalaMetaVersion,
    "org.scala-js" % "scalajs-library_2.10" % "0.6.14",
    "com.typesafe.play" % "play_2.10" % "2.4.10",
    "com.typesafe.akka" % "akka-actor_2.11" % "2.4.17"
  )

  val testScalaLibraryDownloader = Seq(

    "org.scala-lang" % "scala-library" % Scala.latest_2_10 withSources(),
    "org.scala-lang" % "scala-reflect" % Scala.latest_2_10,
    "org.scala-lang" % "scala-compiler" % Scala.latest_2_10,

    "org.scala-lang" % "scala-library" % Scala.latest_2_11 withSources(),
    "org.scala-lang" % "scala-reflect" % Scala.latest_2_11,
    "org.scala-lang" % "scala-compiler" % Scala.latest_2_11,

    "org.scala-lang" % "scala-library" % Scala.latest_2_12 withSources(),
    "org.scala-lang" % "scala-reflect" % Scala.latest_2_12,
    "org.scala-lang" % "scala-compiler" % Scala.latest_2_12
  )


  def sbt012Libs(v: String) = Seq(
    "org.scala-sbt" % "collections" % v,
    "org.scala-sbt" % "interface" % v,
    "org.scala-sbt" % "io" % v,
    "org.scala-sbt" % "ivy" % v,
    "org.scala-sbt" % "logging" % v,
    "org.scala-sbt" % "main" % v,
    "org.scala-sbt" % "process" % v,
    "org.scala-sbt" % "sbt" % v
  )

  def sbt013Libs(v: String): Seq[ModuleID] =
    sbt012Libs(v) ++ Seq(
      "org.scala-sbt" % "main-settings" % v
    )

  // required jars for MockSbt - it adds different versions to test module classpath
  val mockSbtDownloader: Seq[ModuleID] = {
    val vs013 = Seq("0.13.1", "0.13.5", "0.13.7", Sbt.latest)
    val vs012 = Seq(Sbt.latest_0_12)

    vs013.flatMap(sbt013Libs) ++ vs012.flatMap(sbt012Libs)
  }

  val sbtRuntime: Seq[ModuleID] = Seq(
    sbtLaunch,
    sbtStructureExtractor_012,
    sbtStructureExtractor_013,
    sbtStructureExtractor_100
  )
}
