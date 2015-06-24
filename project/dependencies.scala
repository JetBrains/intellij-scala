import sbt._


object Versions {
  val scalaVersion = "2.11.6"
  val ideaVersion = "142-EAP-SNAPSHOT"
  val sbtStructureVersion = "4.1.0"
  val luceneVersion = "4.3.0"
  val aetherVersion = "1.0.0.v20140518"
  val sisuInjectVersion = "2.2.3"
  val wagonVersion = "2.6"
  val httpComponentsVersion = "4.3.1"
}

object Dependencies {
  import Versions._

  val sbtStructureExtractor012 = "org.jetbrains" % "sbt-structure-extractor-0-12" % sbtStructureVersion
  val sbtStructureExtractor013 = "org.jetbrains" % "sbt-structure-extractor-0-13" % sbtStructureVersion
  val sbtLaunch = "org.scala-sbt" % "sbt-launch" % "0.13.8"

  val scalaLibrary = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
  val scalaParserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"
  val sbtStructureCore = "org.jetbrains" % "sbt-structure-core_2.11" % sbtStructureVersion
  val evoInflector = "org.atteo" % "evo-inflector" % "1.2"
  val scalatestFinders = "org.scalatest" % "scalatest-finders" % "0.9.6"

  val plexusContainerDefault = "org.codehaus.plexus" % "plexus-container-default" % "1.5.5"
  val plexusClassworlds = "org.codehaus.plexus" % "plexus-classworlds" % "2.4"
  val plexusUtils = "org.codehaus.plexus" % "plexus-utils" % "3.0.8"
  val plexusComponentAnnotations = "org.codehaus.plexus" % "plexus-component-annotations" % "1.5.5"
  val xbeanReflect = "org.apache.xbean" % "xbean-reflect" % "3.4"

  val luceneCore = "org.apache.lucene" % "lucene-core" % luceneVersion
  val luceneHighlighter = "org.apache.lucene" % "lucene-highlighter" % luceneVersion
  val luceneMemory = "org.apache.lucene" % "lucene-memory" % luceneVersion
  val luceneQueries = "org.apache.lucene" % "lucene-queries" % luceneVersion

  val aetherApi = "org.eclipse.aether" % "aether-api" % aetherVersion
  val aetherUtil = "org.eclipse.aether" % "aether-util" % aetherVersion

  val sisuInjectPlexus = "org.sonatype.sisu" % "sisu-inject-plexus" % sisuInjectVersion
  val sisuInjectBean = "org.sonatype.sisu" % "sisu-inject-bean" % sisuInjectVersion
  val sisuGuice = "org.sonatype.sisu" % "sisu-guice" % "3.0.3"

  val wagonHttp = "org.apache.maven.wagon" % "wagon-http" % wagonVersion
  val wagonHttpShared = "org.apache.maven.wagon" % "wagon-http-shared" % wagonVersion
  val wagonProviderApi = "org.apache.maven.wagon" % "wagon-provider-api" % wagonVersion
  val httpClient = "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion
  val httpCore = "org.apache.httpcomponents" % "httpcore" % httpComponentsVersion
  val commonsLogging = "commons-logging" % "commons-logging" % "1.1.3"
  val commonsCodec = "commons-codec" % "commons-codec" % "1.6"
  val commonsLang = "commons-lang" % "commons-lang" % "2.6"
  val commonsIo = "commons-io" % "commons-io" % "2.2"
  val jsoup = "org.jsoup" % "jsoup" % "1.7.2"

  val mavenIndexerCore = "org.apache.maven.indexer" % "indexer-core" % "6.0"
  val mavenModel = "org.apache.maven" % "maven-model" % "3.0.5"
}

object DependencyGroups {
  import Dependencies._

  val plexusContainer = Seq(
    plexusContainerDefault,
    plexusClassworlds,
    plexusUtils,
    plexusComponentAnnotations,
    xbeanReflect
  )

  val lucene = Seq(
    luceneCore,
    luceneHighlighter,
    luceneMemory,
    luceneQueries
  )

  val aether = Seq(
    aetherApi,
    aetherUtil
  )

  val sisu = Seq(
    sisuInjectPlexus,
    sisuInjectBean,
    sisuGuice
  )

  val wagon = Seq(
    wagonHttp,
    wagonHttpShared,
    wagonProviderApi,
    httpClient,
    httpCore,
    commonsCodec,
    commonsLogging,
    commonsLang,
    commonsIo,
    jsoup
  )

  val mavenIndexer = Seq(
    mavenIndexerCore,
    mavenModel
  ) ++ plexusContainer ++ lucene ++ aether ++ sisu ++ wagon

  val scalaCommunity = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
    scalaParserCombinators,
    sbtStructureCore,
    evoInflector,
    scalatestFinders
  ) ++ mavenIndexer

  val sbtRuntime = Seq(
    sbtStructureExtractor012,
    sbtStructureExtractor013,
    sbtLaunch
  )
}