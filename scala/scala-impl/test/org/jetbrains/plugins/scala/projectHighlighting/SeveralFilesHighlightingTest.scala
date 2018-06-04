package org.jetbrains.plugins.scala.projectHighlighting

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.{inWriteAction, using}
import org.jetbrains.plugins.scala.util.PsiFileTestUtil
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter

import scala.io.Source

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */
trait SeveralFilesHighlightingTest {

  def getProject: Project

  def filesToHighlight: Array[File]

  val reporter: ProgressReporter

  def doTest(): Unit = {
    val files = filesToHighlight.filter(f => f.isDirectory || isScalaFile(f))

    for ((file, idx) <- files.zipWithIndex) {
      if (files.length > 1) {
        reporter.notify(s"${file.getName} (${idx + 1} of ${files.length})")
      }
      annotateFiles(file, reporter)
    }
    reporter.reportResults()
  }

  private def addFileToProject(file: File, relativeTo: File): PsiFile = {
    val text: String = content(file)
    val path = relativeTo.toPath.relativize(file.toPath)
    PsiFileTestUtil.addFileToProject(path, text, getProject)
  }

  private def content(file: File) = using(Source.fromFile(file))(_.getLines.mkString("\n"))

  private def removeFile(psiFile: PsiFile): Unit = {
    inWriteAction(psiFile.delete())
  }

  private def annotateFiles(file: File, reporter: ProgressReporter): Unit = {
    def allFiles(f: File): Seq[File] =
      if (f.isDirectory) f.listFiles.flatMap(allFiles)
      else Seq(f)

    val root = if (file.isDirectory) file else file.getParentFile

    val addedFiles = allFiles(file).map(addFileToProject(_, relativeTo = root))

    try {
      addedFiles.foreach(AllProjectHighlightingTest.annotateFile(_, reporter))
    } finally {
      addedFiles.foreach(removeFile)
    }
  }

  private def isScalaFile(f: File) = f.getName.endsWith(ScalaFileType.INSTANCE.getDefaultExtension)

}