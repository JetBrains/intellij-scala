package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.externalHighlighters.CompilerErrorsListener.compilerMessageHighlightable

private class CompilerErrorsListener extends CompilationStatusListener {

  override def automakeCompilationFinished(errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
    applyErrorsToEditors(compileContext)
  }

  override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
    applyErrorsToEditors(compileContext)
  }

  private def applyErrorsToEditors(context: CompileContext): Unit = {
    if (Registry.is("scala.show.compiler.errors.in.editor")) {
      val errorMap =
        context.getMessages(CompilerMessageCategory.ERROR).toSeq
          .groupBy(_.getVirtualFile)

      ExternalHighlighters.updateOpenEditors(errorMap)
    }
  }
}

private object CompilerErrorsListener {
  implicit val compilerMessageHighlightable: Highlightable[CompilerMessage] = new Highlightable[CompilerMessage] {

    override def severity(info: CompilerMessage): HighlightSeverity = info.getCategory match {
      case CompilerMessageCategory.ERROR   => HighlightSeverity.ERROR
      case CompilerMessageCategory.WARNING => HighlightSeverity.WARNING
      case _                               => HighlightSeverity.INFORMATION
    }

    override def message(message: CompilerMessage): String = {
      val lines = message.getMessage.split('\n')

      if (lines.length > 1) lines.dropRight(1).mkString("\n")
      else message.getMessage
    }

    override def range(message: CompilerMessage, editor: Editor): Option[TextRange] = None

    override def offset(message: CompilerMessage, editor: Editor): Option[Int] = {
      message match {
        case message: CompilerMessageImpl =>
          val line = message.getLine - 1
          val column = (message.getColumn - 1).max(0)
          if (line < 0)
            return None

          val lineStart = editor.getDocument.getLineStartOffset(line)

          Some(lineStart + column)
        case _ =>
          None
      }
    }

    override def virtualFile(t: CompilerMessage): VirtualFile = t.getVirtualFile
  }

}