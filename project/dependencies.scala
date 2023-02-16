import sbt._

import java.io.IOException
import java.net.SocketTimeoutException

object Versions {
  val scalaVersion: String = "2.13.10"
  val scala3Version: String = "3.2.2"

  // ATTENTION: when updating sbtVersion also update versions in MockSbt_1_0
  // NOTE: sbt-launch / bloop-launcher won't be fetched on refresh.
  // run runtimeDependencies/update manually
  val sbtVersion: String = Sbt.latest
  val bloopVersion = "1.5.4"
  val zincVersion = "1.8.0"
  val intellijVersion = "231.7515.9"

  val Utils.DataForManagedIntellijDependencies(
    intellijVersion_ForManagedIntellijDependencies,
    intellijRepository_ForManagedIntellijDependencies
  ) = Utils.getDataForManagedIntellijDependencies(intellijVersion)

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

object Repositories {
  val intellijRepositoryReleases: MavenRepository = MavenRepository("intellij-repository-releases", "https://www.jetbrains.com/intellij-repository/releases")
  val intellijRepositoryEap: MavenRepository = MavenRepository("intellij-repository-eap", "https://www.jetbrains.com/intellij-repository/snapshots")
  //only available in jetbrains network
  val intellijRepositoryNightly: MavenRepository = MavenRepository("intellij-repository-nightly", "https://www.jetbrains.com/intellij-repository/nightly")
}

object Dependencies {

  import Versions._

  val sbtLaunch: ModuleID = "org.scala-sbt" % "sbt-launch" % sbtVersion intransitive()
  val scalaLibrary: ModuleID = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaReflect: ModuleID = "org.scala-lang" % "scala-reflect" % scalaVersion
  val scalaCompiler: ModuleID = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaXml: ModuleID = "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
  val scalaParallelCollections: ModuleID = "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0"
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

  val bcel: ModuleID = "org.apache.bcel" % "bcel" % "6.5.0"

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
  val sbtBridge_Scala_3_2 = "org.scala-lang" % "scala3-sbt-bridge" % "3.2.1"
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
    commonsLang,
    bcel
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

private object Utils {

  case class DataForManagedIntellijDependencies(
    intellijVersion: String,
    intellijRepository: sbt.MavenRepository
  )

  /**
   * Main IntelliJ SDK is managed with sbt-idea-plugin (using org.jetbrains.sbtidea.Keys.intellijBuild key)<br>
   * Some parts of intellij are published as separate libraries, for example some base test classes (see e.g. IDEA-281823 and IDEA-281822)<br>
   * These libraries are managed manually.
   *
   * @param intellijVersion example:<br>
   *                        - 222.2270.15 - Release/EAP version
   *                        - 222.1533 - Nightly version
   *
   * @note Nightly library version can be newer then intellijVersion, because it uses "222-SNAPSHOT" version
   *       It should generally work ok, but there might be some source or binary incompatibilities.
   *       In this case update intellijVersion to the latest Nightly version.
   * @note we might move this feature into sbt-idea-plugin using something like
   *       [[org.jetbrains.sbtidea.download.idea.IJRepoIdeaResolver]]
   */
  def getDataForManagedIntellijDependencies(intellijVersion: String): DataForManagedIntellijDependencies = {
    //Examples of versions of managed artifacts
    //release        : 222.2270.15
    //eap            : 222.2270.15-EAP-SNAPSHOT
    //eap candidate  : 222.2270-EAP-CANDIDATE-SNAPSHOT
    //nightly        : 222.1533

    val versionWithoutTail = intellijVersion.substring(0, intellijVersion.lastIndexOf('.'))
    //222.2270.15 -> 222.2270-EAP-CANDIDATE-SNAPSHOT
    val eapCandidateVersion = versionWithoutTail + "-EAP-CANDIDATE-SNAPSHOT"
    //222.2270 -> 222.2270-SNAPSHOT
    val nightlyVersion = versionWithoutTail + "-SNAPSHOT"
    val eapVersion = intellijVersion + "-EAP-SNAPSHOT"

    val buildType: IdeBuildType =
      if (intellijVersion.count(_ == '.') == 1) IdeBuildType.Nightly
      else if (Utils.isIdeaReleaseBuildAvailable(intellijVersion)) IdeBuildType.Release
      else if (Utils.isIdeaEapBuildAvailable(eapVersion)) IdeBuildType.Eap
      else if (Utils.isIdeaEapBuildAvailable(eapCandidateVersion)) IdeBuildType.EapCandidate
      else {
        val fallback = IdeBuildType.EapCandidate
        val exception = new IllegalStateException(s"Cannot determine build type for version $intellijVersion, fallback to: $fallback (if the fallback isn't resolved from local caches try change it in sources and reload)")
        exception.printStackTrace()
        fallback
      }

    val (intellijVersionManaged, intellijRepositoryManaged) = buildType match {
      case IdeBuildType.Release => (intellijVersion, Repositories.intellijRepositoryReleases)
      case IdeBuildType.Eap => (eapVersion, Repositories.intellijRepositoryEap)
      case IdeBuildType.EapCandidate => (eapCandidateVersion, Repositories.intellijRepositoryEap)
      case IdeBuildType.Nightly => (nightlyVersion, Repositories.intellijRepositoryNightly)
    }
    println(s"""Detected build type for version $buildType (intellij version: $intellijVersion, managed intellij version: $intellijVersionManaged)""")

    DataForManagedIntellijDependencies(intellijVersionManaged, intellijRepositoryManaged)
  }

  private def isIdeaReleaseBuildAvailable(ideaVersion: String): Boolean = {
    val url = Repositories.intellijRepositoryReleases.root + s"/com/jetbrains/intellij/idea/ideaIC/$ideaVersion/ideaIC-$ideaVersion.zip"
    isResourceFound(url)
  }

  private def isIdeaEapBuildAvailable(ideaVersion: String): Boolean = {
    val url = Repositories.intellijRepositoryEap.root + s"/com/jetbrains/intellij/idea/ideaIC/$ideaVersion/ideaIC-$ideaVersion.zip"
    isResourceFound(url)
  }

  private def isResourceFound(urlText: String): Boolean = {
    import java.net.{HttpURLConnection, URL}

    val url = new URL(urlText)
    try {
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.connect()
      val rc = connection.getResponseCode
      connection.disconnect()
      rc != 404
    } catch {
      case _: IOException | _: SocketTimeoutException =>
        //no internet, for example
        false
    }
  }

  sealed trait IdeBuildType
  object IdeBuildType {
    case object Release extends IdeBuildType
    case object Eap extends IdeBuildType
    case object EapCandidate extends IdeBuildType
    case object Nightly extends IdeBuildType
  }
}