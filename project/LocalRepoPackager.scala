import java.net.URI

import sbt._
import sbt.File

/**
  * Download artifacts from jetbrains bintray to mimic a simple local ivy repo that sbt can resolve artifacts from.
  */
object LocalRepoPackager {

  /**
    * Create local plugin repo with given artifacts (artifactId, version).
    * These are all assumed to be in org.jetbrains on the jetbrains sbt-plugins bintray repo
    */
  def localPluginRepo(localRepo: File, paths: Seq[String]): Seq[File] = {
    val jetbrainsRepo = URI.create("https://dl.bintray.com/jetbrains/sbt-plugins/")
    downloadPathsToLocalRepo(jetbrainsRepo, localRepo, paths)
  }

  def localPluginRepoPaths(artifacts: Seq[(String,String)]): Seq[String] =
    artifacts.flatMap { case (artifactId, version) =>
      val artipath = relativeArtifactPath("org.jetbrains", artifactId, version) _
      val plugin_sbt1 = artipath("2.12", "1.0")
      val plugin_sbt013 = artipath("2.10", "0.13")

      Seq(
        jarPath(plugin_sbt013, artifactId), ivyPath(plugin_sbt013),
        jarPath(plugin_sbt1, artifactId), ivyPath(plugin_sbt1)
      )
    }


  /** Download sbt plugin files to a local repo for both sbt 0.13 and 1.0 */
  private def downloadPathsToLocalRepo(remoteRepo: URI, localRepo: File, paths: Seq[String]): Seq[File] = {

    val downloadedArtifactFiles = paths.map { path =>
      val downloadUrl = remoteRepo.resolve(path).normalize().toURL
      val localFile = (localRepo / path).getCanonicalFile
      if (! localFile.exists)
        IO.download(downloadUrl, localFile)
      localFile
    }

    downloadedArtifactFiles
  }

  def relativeArtifactPath(org: String, id: String, version: String)(scalaVersion: String, sbtVersion: String): String =
    s"$org/$id/scala_$scalaVersion/sbt_$sbtVersion/$version"

  def jarPath(artifactPath: String, id: String) = s"$artifactPath/jars/$id.jar"
  def ivyPath(artifactPath: String) = s"$artifactPath/ivys/ivy.xml"

}
