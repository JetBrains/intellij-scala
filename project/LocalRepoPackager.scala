import java.net.URI
import java.nio.charset.StandardCharsets

import sbt._

/**
  * Download artifacts from jetbrains bintray to mimic a simple local ivy repo that sbt can resolve artifacts from.
  */
object LocalRepoPackager {

  /**
    * Create local plugin repo by downloading published files from the jetbrains sbt-plugins bintray repo.
    */
  def localPluginRepo(localRepo: File, paths: Seq[String]): Seq[File] = {
    val jetbrainsRepo = URI.create("https://dl.bintray.com/jetbrains/sbt-plugins/")
    downloadPathsToLocalRepo(jetbrainsRepo, localRepo, paths)
  }

  /**
    * Create paths to download from repo with artifacts given by (artifactId, version).
    */
  def localPluginRepoPaths(artifacts: Seq[(String, String)]): Seq[String] =
    artifacts.flatMap { case (artifactId, version) =>
      val plugin_sbt1 = relativePathSbt1(artifactId, version)
      val plugin_sbt013 = relativePathSbt013(artifactId, version)

      jarPaths(plugin_sbt013, artifactId) ++ srcPaths(plugin_sbt013, artifactId) ++ docPaths(plugin_sbt013, artifactId)++ ivyPaths(plugin_sbt013) ++
      jarPaths(plugin_sbt1, artifactId) ++ srcPaths(plugin_sbt1, artifactId) ++ docPaths(plugin_sbt1, artifactId) ++ ivyPaths(plugin_sbt1)
    }

  /** Download sbt plugin files to a local repo for both sbt 0.13 and 1.0 */
  private def downloadPathsToLocalRepo(remoteRepo: URI, localRepo: File, paths: Seq[String]): Seq[File] = {

    val emptyMD5 = "d41d8cd98f00b204e9800998ecf8427e"

    val downloadedArtifactFiles = paths.map { path =>
      val downloadUrl = remoteRepo.resolve(path).normalize().toURL
      val localFile = (localRepo / path).getCanonicalFile

      // Place dummy javadoc files for artifacts to avoid resolve errors without packaging large-ish but useless files.
      if (!localFile.exists) {
        if (path.endsWith("-javadoc.jar")) {
          IO.write(localFile, Array.empty[Byte])
        } else if (path.endsWith("-javadoc.jar.md5")) {
          IO.write(localFile, emptyMD5.getBytes(StandardCharsets.US_ASCII))
        } else IO.download(downloadUrl, localFile)
      }

      localFile
    }

    downloadedArtifactFiles
  }

  def relativeArtifactPath(org: String, id: String, version: String)(scalaVersion: String, sbtVersion: String): String =
    s"$org/$id/scala_$scalaVersion/sbt_$sbtVersion/$version"

  def relativePathSbt013(artifactId: String, version: String): String =
    relativeArtifactPath("org.jetbrains", artifactId, version)("2.10","0.13")

  def relativePathSbt1(artifactId: String, version: String): String =
    relativeArtifactPath("org.jetbrains", artifactId, version)("2.12","1.0")

  def relativeJarPath013(artifactId: String, version: String): String = {
    val artifactPath = relativePathSbt013(artifactId, version)
    relativeJarPath(artifactPath, artifactId)
  }

  def relativeJarPath1(artifactId: String, version: String): String = {
    val artifactPath = relativePathSbt1(artifactId, version)
    relativeJarPath(artifactPath, artifactId)
  }

  private def relativeJarPath(artifactPath: String, artifactId: String): String =
    s"$artifactPath/jars/$artifactId.jar"

  private def jarPaths(artifactPath: String, id: String) = {
    val path = relativeJarPath(artifactPath, id)
    List(path, md5Path(path))
  }
  private def srcPaths(artifactPath: String, id: String) = {
    val path = s"$artifactPath/srcs/$id-sources.jar"
    List(path, md5Path(path))
  }
  private def docPaths(artifactPath: String, id: String) = {
    val path = s"$artifactPath/docs/$id-javadoc.jar"
    List(path, md5Path(path))
  }
  private def ivyPaths(artifactPath: String) = {
    val path = s"$artifactPath/ivys/ivy.xml"
    List(path, md5Path(path))
  }


  private def md5Path(path: String) = path + ".md5"
}
