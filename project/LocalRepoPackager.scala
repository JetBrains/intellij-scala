import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import sbt._

import scala.sys.process._
import scala.util.Try

/**
  * Download artifacts from jetbrains bintray to mimic a simple local ivy repo that sbt can resolve artifacts from.
  */
object LocalRepoPackager {

  /**
    * Create local plugin repo by downloading published files from the jetbrains sbt-plugins bintray repo.
    */
  def localPluginRepo(localRepo: File, paths: Seq[String]): Seq[File] = {
//    val homeDir = System.getProperty("user.home", "~")
//    val localPublishRepo = Paths.get(homeDir, ".ivy2", "local").toUri
    val jetbrainsRepo = URI.create("https://dl.bintray.com/jetbrains/sbt-plugins/")
    downloadPathsToLocalRepo(Seq(/*localPublishRepo,*/ jetbrainsRepo), localRepo, paths)
  }

  /**
    * Create paths to download from repo with artifacts given by (artifactId, version).
    */
  def localPluginRepoPaths(artifacts: Seq[(String, String, String)]): Seq[String] =
    artifacts.flatMap { case (org, artifactId, version) =>
      val plugin_sbt1 = relativePathSbt1(org, artifactId, version)
      val plugin_sbt013 = relativePathSbt013(org, artifactId, version)

      jarPaths(plugin_sbt013, artifactId) ++ srcPaths(plugin_sbt013, artifactId) ++ docPaths(plugin_sbt013, artifactId)++ ivyPaths(plugin_sbt013) ++
      jarPaths(plugin_sbt1, artifactId) ++ srcPaths(plugin_sbt1, artifactId) ++ docPaths(plugin_sbt1, artifactId) ++ ivyPaths(plugin_sbt1)
    }

  /** Download sbt plugin files to a local repo for both sbt 0.13 and 1.0 */
  private def downloadPathsToLocalRepo(remoteRepos: Seq[URI], localRepo: File, paths: Seq[String]): Seq[File] = {

    val emptyMD5 = "d41d8cd98f00b204e9800998ecf8427e"

    val downloadedArtifactFiles = paths.map { path =>
      val downloadUrls = remoteRepos.map(_.resolve(path).normalize().toURL)
      val localFile = (localRepo / path).getCanonicalFile

      // Place dummy javadoc files for artifacts to avoid resolve errors without packaging large-ish but useless files.
      if (!localFile.exists) {
        if (path.endsWith("-javadoc.jar")) {
          IO.write(localFile, Array.empty[Byte])
        } else if (path.endsWith("-javadoc.jar.md5")) {
          IO.write(localFile, emptyMD5.getBytes(StandardCharsets.US_ASCII))
        } else {
          localFile.getParentFile.mkdirs()
          downloadUrls.find { downloadUrl =>
            Try(downloadUrl #> localFile !!).isSuccess
          }
        }
      }

      localFile
    }

    downloadedArtifactFiles
  }

  def relativeArtifactPath(org: String, id: String, version: String)(scalaVersion: String, sbtVersion: String): String =
    s"$org/$id/scala_$scalaVersion/sbt_$sbtVersion/$version"

  def relativePathSbt013(org: String, artifactId: String, version: String): String =
    relativeArtifactPath(org, artifactId, version)("2.10","0.13")

  def relativePathSbt1(org: String, artifactId: String, version: String): String =
    relativeArtifactPath(org, artifactId, version)("2.12","1.0")

  def relativeJarPath013(org: String, artifactId: String, version: String): String = {
    val artifactPath = relativePathSbt013(org, artifactId, version)
    relativeJarPath(artifactPath, artifactId)
  }

  def relativeJarPath1(org: String, artifactId: String, version: String): String = {
    val artifactPath = relativePathSbt1(org, artifactId, version)
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
