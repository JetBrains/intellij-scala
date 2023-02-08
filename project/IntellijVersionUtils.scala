import sbt.MavenRepository

import java.io.IOException
import java.net.SocketTimeoutException

object IntellijVersionUtils {

  case class DataForManagedIntellijDependencies(
    intellijVersion: String,
    intellijRepository: sbt.MavenRepository
  )

  sealed trait IdeBuildType
  object IdeBuildType {
    case object Release extends IdeBuildType
    case object Eap extends IdeBuildType
    case object EapCandidate extends IdeBuildType
    case object Nightly extends IdeBuildType
  }

  private object Repositories {
    val intellijRepositoryReleases: MavenRepository = MavenRepository("intellij-repository-releases", "https://www.jetbrains.com/intellij-repository/releases")
    val intellijRepositoryEap: MavenRepository = MavenRepository("intellij-repository-eap", "https://www.jetbrains.com/intellij-repository/snapshots")
    //only available in jetbrains network
    val intellijRepositoryNightly: MavenRepository = MavenRepository("intellij-repository-nightly", "https://www.jetbrains.com/intellij-repository/nightly")
  }

  /**
   * Main IntelliJ SDK is managed with sbt-idea-plugin (using org.jetbrains.sbtidea.Keys.intellijBuild key)<br>
   * Some parts of intellij are published as separate libraries, for example some base test classes (see e.g. IDEA-281823 and IDEA-281822)<br>
   * These libraries are managed manually.
   *
   * @param intellijVersion example:<br>
   *                        - 222.2270.15 - Release/EAP version
   *                        - 222.1533 - Nightly version
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
      else if (isIdeaReleaseBuildAvailable(intellijVersion)) IdeBuildType.Release
      else if (isIdeaEapBuildAvailable(eapVersion)) IdeBuildType.Eap
      else if (isIdeaEapBuildAvailable(eapCandidateVersion)) IdeBuildType.EapCandidate
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
}
