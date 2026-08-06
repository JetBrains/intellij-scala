package org.jetbrains.plugins.scala.internal.bundle

import org.jetbrains.plugins.scala.extensions.PathExt

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
      file.getFileName.toString.endsWith("Bundle.properties") &&
        file.parents.nextOption().exists(_.getFileName.toString == "messages") &&
        file.parents.drop(1).nextOption().exists(_.getFileName.toString == "resources")
    })

  //example: scala-coverage.xml, intellij-qodana-jvm-sbt.xml
  def findAllIdeaPluginXmlFiles(
    root: Path,
    ignoreDirNames: Seq[String] = DefaultIgnoredDirs
  ): Seq[Path] =
    findAllFiles(root, ignoreDirNames, file => {
      file.getFileName.toString.endsWith(".xml") &&
        file.parents.nextOption().exists(_.getFileName.toString == "META-INF") &&
        file.parents.drop(1).nextOption().exists(_.getFileName.toString == "resources")
    })

  //example: scala-coverage.xml, intellij-qodana-jvm-sbt.xml
  def findAllFiles(
    root: Path,
    ignoreDirNames: Seq[String] = DefaultIgnoredDirs,
    accept: Path => Boolean
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
        if (accept(path)) {
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
