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

  def downloadDotty(version: String, listener: String => Unit, contextDirectory: VirtualFile) {
    val root = virtualToIoFile(contextDirectory) / "lib"
    DottyVersions.loadCompilerTo(version, root)

    def downloadScala(kind: String) = {
      val libraryVersion = "2.11.5"
      downloadContentToFile(null,
        s"http://repo1.maven.org/maven2/org/scala-lang/$kind/$libraryVersion/$kind-$libraryVersion.jar",
        root / s"$kind-$libraryVersion.jar")
    }

    downloadScala("scala-library")
    downloadScala("scala-reflect")
  }
}
