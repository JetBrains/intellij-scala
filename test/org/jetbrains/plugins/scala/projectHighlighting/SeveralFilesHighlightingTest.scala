package org.jetbrains.plugins.scala.projectHighlighting

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.PsiFileTestUtil
import org.jetbrains.plugins.scala.util.reporter.{ConsoleReporter, ProgressReporter}

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */
trait SeveralFilesHighlightingTest {

  def getProject: Project

  def filesToHighlight: Array[File]

  val reporter: ProgressReporter = new ConsoleReporter

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

  private def addFileToProject(file: File): PsiFile = {
    val source = scala.io.Source.fromFile(file)
    val text = try source.getLines().mkString("\n") finally source.close()
    PsiFileTestUtil.addFileToProject(file.getName, text, getProject)
  }

  private def removeFile(psiFile: PsiFile): Unit = {
    inWriteAction(psiFile.delete())
  }

  private def annotateFiles(file: File, reporter: ProgressReporter): Unit = {
    val filesToAdd =
      if (file.isDirectory) file.listFiles().filterNot(_.isDirectory)
      else Array(file)

    val addedFiles = filesToAdd.map(addFileToProject)

    try {
      addedFiles.foreach(AllProjectHighlightingTest.annotateFile(_, reporter))
    } finally {
      addedFiles.foreach(removeFile)
    }
  }

  private def isScalaFile(f: File) = f.getName.endsWith(ScalaFileType.INSTANCE.getDefaultExtension)

}