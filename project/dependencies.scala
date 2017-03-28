import sbt._
import meta.Shared

object Versions {
  val scalaVersion = "2.11.8"
  val sbtVersion = "0.13.13"
  val ideaVersion = "171.3780.52"
  val sbtStructureVersion: String = Shared.sbtStructureVersion
  val luceneVersion = "4.8.1"
  val aetherVersion = "1.0.0.v20140518"
  val sisuInjectVersion = "2.2.3"
  val wagonVersion = "2.6"
  val httpComponentsVersion = "4.3.1"
}

object Dependencies {
  import Versions._

  val sbtStructureExtractor012: ModuleID = "org.jetbrains" % "sbt-structure-extractor-0-12" % sbtStructureVersion
  val sbtStructureExtractor013: ModuleID = "org.jetbrains" % "sbt-structure-extractor-0-13" % sbtStructureVersion
  val sbtLaunch: ModuleID = "org.scala-sbt" % "sbt-launch" % sbtVersion

  val jamm: ModuleID = "com.github.jbellis" % "jamm" % "0.3.1"
  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
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
  val macroParadise: ModuleID = "org.scalameta" % "paradise" % "3.0.0-M7" cross CrossVersion.full
  val scalaMetaCore: ModuleID = "org.scalameta" % "scalameta_2.11" % "1.6.0" withSources()

  val nailgun: ModuleID = "org.jetbrains" % "nailgun-patched" % "1.0.0"
  val compilerInterfaceSources: ModuleID = "org.jetbrains" % "compiler-interface-sources" % "1.0.0"
  val bundledJline: ModuleID = "org.jetbrains" % "jline" % "1.0.0"
  val incrementalCompiler: ModuleID = "org.jetbrains" % "incremental-compiler" % "1.0.0"
  val sbtInterface: ModuleID = "org.jetbrains" % "sbt-interface" % "1.0.0"
  val dottyInterface: ModuleID = "ch.epfl.lamp" % "dotty-interfaces" % "0.1.1-20170227-179a5d6-NIGHTLY"
}

object DependencyGroups {
  import Dependencies._

  val sbtBundled = Seq(
    compilerInterfaceSources,
    bundledJline,
    incrementalCompiler,
    sbtInterface
  )

  val scalastyle = Seq(
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
        "0.13.9", "0.13.11", "0.13.12", "0.13.13")
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
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.2",
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
    "org.scalameta" % "paradise_2.11.8" % "3.0.0-M5" exclude("org.scalameta", "scalameta_2.11"),
    "org.scala-js" % "scalajs-library_2.10" % "0.6.14",
    "com.typesafe.play" % "play_2.10" % "2.4.10",
    "com.typesafe.akka" % "akka-actor_2.11" % "2.4.17"
  )

  val testScalaLibraryDownloader = Seq(
    "org.scala-lang" % "scala-library" % "2.10.6" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.10.6",
    "org.scala-lang" % "scala-compiler" % "2.10.6",

    "org.scala-lang" % "scala-library" % "2.11.7" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.11.7",
    "org.scala-lang" % "scala-compiler" % "2.11.7",

    "org.scala-lang" % "scala-library" % "2.12.0" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.12.0",
    "org.scala-lang" % "scala-compiler" % "2.12.0"
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

  def sbt013Libs(v: String): Seq[ModuleID] = sbt012Libs(v) ++ Seq(
    "org.scala-sbt" % "main-settings" % v
  )

  // required jars for MockSbt - it adds different versions to test module classpath
  val mockSbtDownloader: Seq[ModuleID] = {
    val latest = "0.13.13" // maybe we should supply latest sbt via BuildInfo to Sbt.LatestVersion
    val vs013 = Seq("0.13.1", "0.13.5", "0.13.7", latest)
    val vs012 = Seq("0.12.4")

    vs013.flatMap(sbt013Libs) ++ vs012.flatMap(sbt012Libs)
  }

  val sbtRuntime = Seq(
    sbtStructureExtractor012,
    sbtStructureExtractor013,
    sbtLaunch
  )
}
