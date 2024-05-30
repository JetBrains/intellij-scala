package org.jetbrains.plugins.scala.internal.bundle

import java.io.File
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import scala.collection.mutable

object ScalaPluginResourcesUtils {

  val DefaultIgnoredDirs: Seq[String] = Seq(
    ".idea",
    ".git",
    "target"
  )

  def findAllBundleFiles(
    root: Path,
    ignoreDirNames: Seq[String] = DefaultIgnoredDirs
  ): Seq[Path] =
    findAllFiles(root, ignoreDirNames, file => {
      file.getName.endsWith("Bundle.properties") &&
        file.getParentFile.getName == "messages" &&
        file.getParentFile.getParentFile.getName == "resources"
    })

  //example: scala-coverage.xml, intellij-qodana-jvm-sbt.xml
  def findAllIdeaPluginXmlFiles(
    root: Path,
    ignoreDirNames: Seq[String] = DefaultIgnoredDirs
  ): Seq[Path] =
    findAllFiles(root, ignoreDirNames, file => {
      file.getName.endsWith(".xml") &&
        file.getParentFile.getName == "META-INF" &&
        file.getParentFile.getParentFile.getName == "resources"
    })

  //example: scala-coverage.xml, intellij-qodana-jvm-sbt.xml
  def findAllFiles(
    root: Path,
    ignoreDirNames: Seq[String] = DefaultIgnoredDirs,
    accept: File => Boolean
  ): Seq[Path] = {
    assert(Files.exists(root))

    val result = mutable.ArrayBuffer.empty[Path]
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
        if (ignoreDirNames.contains(dir.getFileName.toString))
          FileVisitResult.SKIP_SUBTREE
        else
          FileVisitResult.CONTINUE

      override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val file = path.toFile
        if (accept(file)) {
          result += path
          FileVisitResult.CONTINUE
        }
        else
          FileVisitResult.CONTINUE
      }
    })

    result.toSeq
  }
}