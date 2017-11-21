import sbt._

object Versions {
  val scalaVersion: String = Scala.latest_2_12
  val scalaBinaryVersion: String = Scala.binary_2_12
  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  val sbtVersion: String = Sbt.latest
  val zincVersion = "1.0.3"
  val ideaVersion = "173.3727.22"
  val sbtStructureVersion: String = "2017.2"
  val sbtIdeaShellVersion: String = "2017.2"
  val aetherVersion = "1.0.0.v20140518"
  val sisuInjectVersion = "2.2.3"
  val wagonVersion = "2.6"
  val httpComponentsVersion = "4.3.1"
  val scalaMetaVersion = "1.8.0"
  val paradiseVersion = "3.0.0-M10"
  val monocleVersion = "1.4.0"
  val scalazVersion = "7.1.0"
  val ScalamacrosVersion = "2.0.0-94-f03bbf3a"

  object Scala {
    val binary_2_9 = "2.9.2"
    val binary_2_10 = "2.10"
    val binary_2_11 = "2.11"
    val binary_2_12 = "2.12"

    // ATTENTION: When changing any of these versions,
    // they currently need to be updated in org.jetbrains.plugins.scala.debugger.ScalaVersion
    val latest_2_9 = "2.9.3"
    val latest_2_10 = "2.10.7"
    val latest_2_11 = "2.11.12"
    val latest_2_12 = "2.12.3" // don't upgrade to 2.12.4 because it breaks compilation. https://github.com/scala/bug/issues/10568
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
    val binary_1_0 = "1.0"

    val latest_0_12 = "0.12.4"
    val latest_0_13 = "0.13.16"
    val latest_1_0 = "1.0.3"
    val latest: String = latest_1_0

    // these need to be updated to correspond to the versions in sbt/project/Dependencies.scala
    // they are required for our tests. TODO: automatically update them based on sbt base version
    val latestIo = "1.0.2"
    val latestUtil = "1.0.2"
    val latestLm = "1.0.3"

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
  val ivy2: ModuleID = "org.apache.ivy" % "ivy" % "2.4.0" % "test"

  val scalastyle: ModuleID = "org.scalastyle" %% "scalastyle" % "1.0.0"
  val scalariform: ModuleID = "org.scalariform" %% "scalariform" % "0.2.2"
  val macroParadise: ModuleID = "org.scalameta" % "paradise" % paradiseVersion cross CrossVersion.full
  val scalaMetaCore: ModuleID = "org.scalameta" %% "scalameta" % scalaMetaVersion withSources() exclude("com.google.protobuf", "protobuf-java")
  val fastparse: ModuleID = "com.lihaoyi" % s"fastparse_$scalaBinaryVersion" % "0.4.3" // transitive dependency of scalaMeta, needs explicit versioning
  val scalaMacros2: ModuleID = "org.scalamacros" %% "scalamacros" % ScalamacrosVersion

  val bcel: ModuleID = "org.apache.bcel" % "bcel" % "6.0"

  val nailgun: ModuleID = "org.jetbrains" % "nailgun-patched" % "1.0.0"
  val zinc = "org.scala-sbt" %% "zinc" % zincVersion
  val zincInterface = "org.scala-sbt" % "compiler-interface" % zincVersion
  val sbtInterface = "org.scala-sbt" % "util-interface" % "1.0.0"

  val compilerBridgeSources_2_10 = "org.scala-sbt" % "compiler-bridge_2.10" % zincVersion classifier "sources"
  val compilerBridgeSources_2_11 = "org.scala-sbt" % "compiler-bridge_2.11" % zincVersion classifier "sources"

  private def sbtPluginDependency(module: ModuleID, sbtVersion: String): ModuleID =
    sbt.Defaults.sbtPluginExtra(module, sbtVersion, Sbt.scalaVersion(sbtVersion))

}

object DependencyGroups {
  import Dependencies._
  import Versions._

  val sbtBundled = Seq(
    zinc,
    zincInterface
  )

  val scalaCommunity: Seq[ModuleID] = Seq(
    scalaLibrary,
    scalaReflect,
    scalaXml,
    scalaMetaCore,
    scalaMacros2,
    scalaParserCombinators,
    sbtStructureCore,
    evoInflector,
    scalatestFindersPatched,
    jamm,
    scalastyle,
    scalariform
  )

  val decompiler = Seq(
    scalaLibrary,
    scalaReflect,
    scalaCompiler,
    commonsLang,
    bcel
  )

  val runners = Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion,
    specs2,
    "org.scalatest" %% "scalatest" % "3.0.1" % "provided",
    "com.lihaoyi" %% "utest" % "0.5.4" % "provided"
  )

  val sbtLaunchTestDownloader: Seq[ModuleID] =
    Seq("0.12.4", "0.13.0", "0.13.5", "0.13.9", Sbt.latest_0_13, Sbt.latest)
      .distinct
      .map(v => "org.scala-sbt" % "sbt-launch" % v)

  val testDownloader = Seq(

    "com.chuusai" % "shapeless_2.11" % "2.0.0",
    "com.fommil" % "stalactite_2.11" % "0.0.3",
    "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
    "com.github.julien-truffaut" %% "monocle-generic" % monocleVersion,
    "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
    "com.github.mpilquist" % "simulacrum_2.11" % "0.10.0",
    "com.lihaoyi" % "utest_2.10" % "0.3.1" % "provided",
    "com.lihaoyi" % "utest_2.10" % "0.4.3" % "provided",
    "com.lihaoyi" % "utest_2.11" % "0.3.1" % "provided",
    "com.lihaoyi" % "utest_2.11" % "0.4.3" % "provided",
    "com.lihaoyi" % "utest_2.11" % "0.5.4" % "provided",
    "com.typesafe.akka" % "akka-actor_2.11" % "2.4.19",
    "com.typesafe.akka" % "akka-stream_2.11" % "2.4.19",
    "com.typesafe.play" % "play_2.10" % "2.4.10",
    "com.typesafe.slick" % "slick_2.11" % "3.2.1",
    "org.scala-lang.modules" % "scala-async_2.11" % "0.9.5",
    "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.6",
    "org.scala-lang.modules" % "scala-xml_2.12" % "1.0.6",
    "org.scala-js" % "scalajs-library_2.10" % "0.6.14",
    "org.scalaz" % "scalaz-core_2.10" % scalazVersion,
    "org.scalaz" % "scalaz-concurrent_2.10" % scalazVersion,
    "org.scalaz" % "scalaz-core_2.11" % scalazVersion,
    "org.scalaz" % "scalaz-concurrent_2.11" % scalazVersion,
    "org.scalaz.stream" % "scalaz-stream_2.11" % "0.6a",
    "org.specs2" % "specs2_2.10" % "2.4.6",
    "org.specs2" % "specs2_2.11" % "2.4.15",
    "org.specs2" % "specs2-core_2.11" % "3.0.1",
    "org.specs2" % "specs2-core_2.12" % "4.0.0",
    "org.specs2" % "specs2-common_2.11" % "3.0.1",
    "org.specs2" % "specs2-common_2.12" % "4.0.0",
    "org.specs2" % "specs2-matcher_2.11" % "3.0.1",
    "org.specs2" % "specs2-matcher_2.12" % "4.0.0",
    "org.specs2" % "specs2-fp_2.12" % "4.0.0",
    "org.typelevel" % "scodec-bits_2.11" % "1.1.0-SNAPSHOT",
    "org.typelevel" % "scodec-core_2.11" % "1.7.0-SNAPSHOT",
    "org.scalatest" % "scalatest_2.10" % "1.9.2",
    "org.scalatest" % "scalatest_2.10" % "2.1.7",
    "org.scalatest" % "scalatest_2.10" % "2.2.1",
    "org.scalatest" % "scalatest_2.11" % "2.1.7",
    "org.scalatest" % "scalatest_2.11" % "2.2.1",
    "org.scalatest" % "scalatest_2.11" % "3.0.1",
    "org.scalactic" % "scalactic_2.11" % "3.0.1",
    "org.scalactic" % "scalactic_2.12" % "3.0.4",
    "org.scalatest" % "scalatest_2.12" % "3.0.4",
    "org.scalameta" % s"paradise_$scalaVersion" % paradiseVersion exclude("org.scalameta", s"scalameta_$scalaBinaryVersion"),
    "org.scalameta" % "scalameta_2.11" % scalaMetaVersion,
    "org.scalameta" % "scalameta_2.12" % scalaMetaVersion,
    "org.typelevel" % "cats_2.11" % "0.4.0"
  )

  val testScalaLibraryDownloader = Seq(

    "org.scala-lang" % "scala-library" % Scala.latest_2_9 withSources(),
    "org.scala-lang" % "scala-compiler" % Scala.latest_2_9,

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

  val sbtOrg = "org.scala-sbt"

  def sbt012Libs(v: String) = Seq(
    sbtOrg % "collections" % v,
    sbtOrg % "interface" % v,
    sbtOrg % "io" % v,
    sbtOrg % "ivy" % v,
    sbtOrg % "logging" % v,
    sbtOrg % "main" % v,
    sbtOrg % "process" % v,
    sbtOrg % "sbt" % v
  )

  def sbt013Libs(v: String): Seq[ModuleID] =
    sbt012Libs(v) ++ Seq(
      sbtOrg % "main-settings" % v
    )

  val sbt1CrossScala: CrossVersion = CrossVersion.fullMapped(_ => Scala.binary_2_12)
//  def sbt100Libs(v:String): Seq[ModuleID] =
//    // these are not cross-versioned
//    Seq("sbt", "util-interface", "test-agent").map(lib => sbtOrg % lib % v) ++
//    // this has separate versioning
//    Seq(sbtOrg % "compiler-interface" % zincVersion) ++
//    // all of these are published cross-versioned for scala 2.12
//    Seq(
//      "main","logic","collections","util-position","util-relation","actions","completion","io",
//      "util-control","run","util-logging","task-system","tasks","util-cache",
//      "testing","util-tracking","main-settings","command","protocol","core-macros", "librarymanagement-core"
//    ).map(lib => (sbtOrg % lib % v).withCrossVersion(sbt1CrossScala))

  def sbt100Libs(v:String): Seq[ModuleID] = Seq(sbtOrg % "sbt" % v) // all the modules are transitive deps

  // required jars for MockSbt - it adds different versions to test module classpath
  val mockSbtDownloader: Seq[ModuleID] = {
    val vs100 = Seq(Sbt.latest_1_0)
    val vs013 = Seq("0.13.1", "0.13.5", "0.13.7", Sbt.latest_0_13)
    val vs012 = Seq(Sbt.latest_0_12)

    vs100.flatMap(sbt100Libs) ++ vs013.flatMap(sbt013Libs) ++ vs012.flatMap(sbt012Libs)
  }

  val sbtRuntime: Seq[ModuleID] = Seq(
    sbtLaunch,
    sbtStructureExtractor_012,
    sbtStructureExtractor_013,
    sbtStructureExtractor_100,
    compilerBridgeSources_2_10,
    compilerBridgeSources_2_11
  )
}
