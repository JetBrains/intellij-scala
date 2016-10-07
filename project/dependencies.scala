import sbt._


object Versions {
  val scalaVersion = "2.11.6"
  val sbtVersion = "0.13.11"
  val ideaVersion = "163.5644.15"
  val sbtStructureVersion = "6.0.2"
  val luceneVersion = "4.8.1"
  val aetherVersion = "1.0.0.v20140518"
  val sisuInjectVersion = "2.2.3"
  val wagonVersion = "2.6"
  val httpComponentsVersion = "4.3.1"
}

object Dependencies {
  import Versions._

  val sbtStructureExtractor012 = "org.jetbrains" % "sbt-structure-extractor-0-12" % sbtStructureVersion
  val sbtStructureExtractor013 = "org.jetbrains" % "sbt-structure-extractor-0-13" % sbtStructureVersion
  val sbtLaunch = "org.scala-sbt" % "sbt-launch" % sbtVersion

  val jamm = "com.github.jbellis" % "jamm" % "0.3.1"
  val scalaLibrary = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
  val scalaParserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
  val sbtStructureCore = "org.jetbrains" % "sbt-structure-core_2.11" % sbtStructureVersion
  val evoInflector = "org.atteo" % "evo-inflector" % "1.2"
  val scalatestFindersPatched = "org.scalatest" % "scalatest-finders-patched" % "0.9.8"

//  val plexusContainerDefault = "org.codehaus.plexus" % "plexus-container-default" % "1.5.5"
//  val plexusClassworlds = "org.codehaus.plexus" % "plexus-classworlds" % "2.4"
//  val plexusUtils = "org.codehaus.plexus" % "plexus-utils" % "3.0.8"
//  val plexusComponentAnnotations = "org.codehaus.plexus" % "plexus-component-annotations" % "1.5.5"
//  val xbeanReflect = "org.apache.xbean" % "xbean-reflect" % "3.4"
//  val log4j = "log4j" % "log4j" % "1.2.17"

  val luceneCore = "org.apache.lucene" % "lucene-core" % luceneVersion
//  val luceneHighlighter = "org.apache.lucene" % "lucene-highlighter" % luceneVersion
//  val luceneMemory = "org.apache.lucene" % "lucene-memory" % luceneVersion
//  val luceneQueries = "org.apache.lucene" % "lucene-queries" % luceneVersion
//  val luceneQueryParser = "org.apache.lucene" % "lucene-queryparser" % luceneVersion
//  val luceneAnalyzers = "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion
//  val luceneSandbox = "org.apache.lucene" % "lucene-sandbox" % luceneVersion

//  val aetherApi = "org.eclipse.aether" % "aether-api" % aetherVersion
//  val aetherUtil = "org.eclipse.aether" % "aether-util" % aetherVersion

//  val sisuInjectPlexus = "org.sonatype.sisu" % "sisu-inject-plexus" % sisuInjectVersion
//  val sisuInjectBean = "org.sonatype.sisu" % "sisu-inject-bean" % sisuInjectVersion
//  val sisuGuice = "org.sonatype.sisu" % "sisu-guice" % "3.0.3"

//  val wagonHttp = "org.apache.maven.wagon" % "wagon-http" % wagonVersion
//  val wagonHttpShared = "org.apache.maven.wagon" % "wagon-http-shared" % wagonVersion
//  val wagonProviderApi = "org.apache.maven.wagon" % "wagon-provider-api" % wagonVersion
//  val httpClient = "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion
//  val httpCore = "org.apache.httpcomponents" % "httpcore" % httpComponentsVersion
//  val commonsLogging = "commons-logging" % "commons-logging" % "1.1.3"
//  val commonsCodec = "commons-codec" % "commons-codec" % "1.6"
  val commonsLang = "commons-lang" % "commons-lang" % "2.6"
//  val commonsIo = "commons-io" % "commons-io" % "2.2"
//  val jsoup = "org.jsoup" % "jsoup" % "1.7.2"

//  val mavenIndexerCore = "org.apache.maven.indexer" % "indexer-core" % "6.0"
//  val mavenModel = "org.apache.maven" % "maven-model" % "3.0.5"

  val junitInterface = "com.novocode" % "junit-interface" % "0.11" % "test"

  val scalastyle_2_11 = "org.scalastyle" % "scalastyle_2.11" % "0.8.0"
  val scalariform_2_11 = "org.scalariform" % "scalariform_2.11" % "0.1.7"
  val macroParadise = "org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full

  val nailgun = "org.jetbrains" % "nailgun-patched" % "1.0.0"
  val compilerInterfaceSources = "org.jetbrains" % "compiler-interface-sources" % "1.0.0"
  val bundledJline = "org.jetbrains" % "jline" % "1.0.0"
  val incrementalCompiler = "org.jetbrains" % "incremental-compiler" % "1.0.0"
  val sbtInterface = "org.jetbrains" % "sbt-interface" % "1.0.0"
}

object DependencyGroups {
  import Dependencies._

  val sbtBundled = Seq(
    compilerInterfaceSources,
    bundledJline,
    incrementalCompiler,
    sbtInterface
  )

//  val plexusContainer = Seq(
//    plexusContainerDefault,
//    plexusClassworlds,
//    plexusUtils,
//    plexusComponentAnnotations,
//    xbeanReflect,
//    log4j
//  )

//  val lucene = Seq(
//    luceneCore,
//    luceneHighlighter,
//    luceneMemory,
//    luceneQueries,
//    luceneQueryParser,
//    luceneAnalyzers,
//    luceneSandbox
//  )

//  val aether = Seq(
//    aetherApi,
//    aetherUtil
//  )

//  val sisu = Seq(
//    sisuInjectPlexus,
//    sisuInjectBean,
//    sisuGuice
//  )

//  val wagon = Seq(
//    wagonHttp,
//    wagonHttpShared,
//    wagonProviderApi,
//    httpClient,
//    httpCore,
//    commonsCodec,
//    commonsLogging,
//    commonsLang,
//    commonsIo,
//    jsoup
//  )

  val scalastyle = Seq(
    scalastyle_2_11,
    scalariform_2_11
  )

//  val mavenIndexer = Seq(
//    mavenIndexerCore,
//    mavenModel
//  ) ++ plexusContainer ++ lucene ++ aether ++ sisu ++ wagon

  val scalaCommunity = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
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

  val sbtLaunchTestDownloader =
    Seq("0.12.4", "0.13.0", "0.13.1", "0.13.2",
        "0.13.5", "0.13.6", "0.13.7", "0.13.8",
        "0.13.9", "0.13.11", "0.13.12")
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
    "com.github.julien-truffaut"  %%  "monocle-core"    % "1.2.0",
    "com.github.julien-truffaut"  %%  "monocle-generic" % "1.2.0",
    "com.github.julien-truffaut"  %%  "monocle-macro"   % "1.2.0",
    "io.spray" %% "spray-routing" % "1.3.1",
    "com.typesafe.slick" %% "slick" % "3.1.0",
    "org.scala-lang.modules" % "scala-async_2.11" % "0.9.5",
    "org.typelevel" %% "cats" % "0.4.0"
  )

  val testScalaLibraryDownloader = Seq(
    "org.scala-lang" % "scala-library" % "2.10.6" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.10.6",
    "org.scala-lang" % "scala-compiler" % "2.10.6",

    "org.scala-lang" % "scala-library" % "2.11.7" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.11.7",
    "org.scala-lang" % "scala-compiler" % "2.11.7",

    "org.scala-lang" % "scala-library" % "2.12.0-M4" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.12.0-M4",
    "org.scala-lang" % "scala-compiler" % "2.12.0-M4",

    "org.scala-lang" % "scala-library" % "2.12.0-M5" withSources(),
    "org.scala-lang" % "scala-reflect" % "2.12.0-M5",
    "org.scala-lang" % "scala-compiler" % "2.12.0-M5"
  )

  val mockSbtDownloader = {
    val v = "0.13.5"
    Seq(
      "org.scala-sbt" % "collections" % v,
      "org.scala-sbt" % "interface" % v,
      "org.scala-sbt" % "io" % v,
      "org.scala-sbt" % "ivy" % v,
      "org.scala-sbt" % "logging" % v,
      "org.scala-sbt" % "main" % v,
      "org.scala-sbt" % "main-settings" % v,
      "org.scala-sbt" % "process" % v,
      "org.scala-sbt" % "sbt" % v
    )
  }

  val sbtRuntime = Seq(
    sbtStructureExtractor012,
    sbtStructureExtractor013,
    sbtLaunch
  )
}
