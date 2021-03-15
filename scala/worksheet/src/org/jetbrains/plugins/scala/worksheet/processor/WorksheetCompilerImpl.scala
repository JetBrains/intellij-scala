package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.concurrent.duration.DurationInt

final class WorksheetCompilerImpl
  extends org.jetbrains.plugins.scala.externalHighlighters.compiler.WorksheetHighlightingCompiler {

  override def compile(file: PsiFile, document: Document, client: Client): Unit = {
    val scalaFile = file match {
      case sf: ScalaFile if sf.isWorksheetFile => sf
      case _ =>
        return
    }

    if (file.getProject.isDisposed)
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
