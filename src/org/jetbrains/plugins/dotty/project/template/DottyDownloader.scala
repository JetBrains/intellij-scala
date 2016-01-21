package org.jetbrains.plugins.dotty.project.template

import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.dotty.project.DottyVersions
import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.sbt.project.template.activator.ActivatorDownloadUtil._

/**
  * @author adkozlov
  */
object DottyDownloader {
  private val MavenUrl = "http://repo1.maven.org/maven2"

  def downloadDotty(version: String, listener: String => Unit, contextDirectory: VirtualFile) {
    val root = virtualToIoFile(contextDirectory) / "lib"
    DottyVersions.loadCompilerTo(version, root)

    def downloadLibrary(url: String, libraryName: String, version: String) {
      val fileName = jarFileName(libraryName, version)
      downloadContentToFile(null, s"$url/$libraryName/$version/$fileName", root / fileName)
    }

    def downloadScalaLibrary: String => Unit = downloadLibrary(s"$MavenUrl/org/scala-lang", _, "2.11.5")

    downloadScalaLibrary("scala-library")
    downloadScalaLibrary("scala-reflect")

    downloadLibrary("https://oss.sonatype.org/content/repositories/releases/me/d-d/",
      "scala-compiler", "2.11.5-20151022-113908-7fb0e653fd")

    def downloadJLine() {
      val libraryName = "jline"
      downloadLibrary(s"$MavenUrl/$libraryName", libraryName, "2.12")
    }
    downloadJLine()
  }

  private def jarFileName(libraryName: String, version: String) = s"$libraryName-$version.jar"
}
