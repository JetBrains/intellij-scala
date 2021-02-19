package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.externalHighlighters.{CompilerEventGeneratingClient, HighlightingCompiler, HighlightingCompilerHelper, ScalaHighlightingMode}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.RescheduledExecutor

import scala.concurrent.duration.DurationInt

final class WorksheetHighlightingCompiler extends HighlightingCompilerHelper {

  override def canHighlight(file: PsiFile): Boolean = file match {
    case scalaFile: ScalaFile => scalaFile.isWorksheetFile
    case _                    => false
  }

  override def runHighlightingCompilation(project: Project, file: PsiFile, document: Document, client: Client): Unit = {
    val scalaFile = file match {
      case sf: ScalaFile => sf
      case _ =>
        return
    }
    if (project.isDisposed)
      return

    val module = scalaFile.module.getOrElse(return)
    val compiler = new WorksheetCompiler(module, scalaFile)
    compiler.compileOnlySync(
      document,
      client,
      waitAtMost = 60.seconds
    )
  }
}
