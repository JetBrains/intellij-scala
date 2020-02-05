package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.{Document, Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.externalHighlighters.CompilerErrorsListener.compilerMessageHighlightable
import org.jetbrains.plugins.scala.settings.ProblemSolverUtils

private class CompilerErrorsListener extends CompilationStatusListener {

  override def automakeCompilationFinished(errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
    compilationFinished(compileContext)
  }

  override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
    compilationFinished(compileContext)
  }

  private def compilationFinished(context: CompileContext): Unit = {
    val project = context.getProject
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      storeMessages(context)

      ExternalHighlighters.updateOpenEditors(project, getState(project).currentMessages)
      informWolf(project)
    }
  }

  private def storeMessages(context: CompileContext): Unit = {
    val state = getState(context.getProject)

    state.errorsByFile   = context.getMessages(CompilerMessageCategory.ERROR).toSeq.groupBy(_.getVirtualFile)
    state.warningsByFile = context.getMessages(CompilerMessageCategory.WARNING).toSeq.groupBy(_.getVirtualFile)
  }

  private def getState(project: Project) =
    ServiceManager.getService(project, classOf[CompilerErrorsListener.State])

  private def informWolf(project: Project): Unit = {
    ProblemSolverUtils.clearAllProblemsFromExternalSource(project, CompilerErrorsListener)
    val wolf = WolfTheProblemSolver.getInstance(project)
    getState(project).errorsByFile.keys.foreach(wolf.reportProblemsFromExternalSource(_, CompilerErrorsListener))
  }
}

private object CompilerErrorsListener {

  private class State(project: Project) {

    var errorsByFile  : Map[VirtualFile, Seq[CompilerMessage]] = Map.empty
    var warningsByFile: Map[VirtualFile, Seq[CompilerMessage]] = Map.empty

    def currentMessages(file: VirtualFile): Seq[CompilerMessage] =
      errorsByFile.getOrElse(file, Nil) ++ warningsByFile.getOrElse(file, Nil)

    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener {
      override def editorCreated(event: EditorFactoryEvent): Unit = {
        val editor = event.getEditor
        ExternalHighlighters.applyHighlighting(project, editor, currentMessages)
      }
    }, project)
  }

  implicit val compilerMessageHighlightable: Highlightable[CompilerMessage] = new Highlightable[CompilerMessage] {

    override def severity(info: CompilerMessage): HighlightSeverity = info.getCategory match {
      case CompilerMessageCategory.ERROR   => HighlightSeverity.ERROR
      case CompilerMessageCategory.WARNING => HighlightSeverity.WARNING
      case _                               => HighlightSeverity.INFORMATION
    }

    override def message(message: CompilerMessage): String = {
      val messageText = message.getMessage
      messageText.trim.stripSuffix(lineText(messageText))
    }

    override def range(message: CompilerMessage, editor: Editor): Option[TextRange] = None

    override def offset(message: CompilerMessage, editor: Editor): Option[Int] = {
      message match {
        case message: CompilerMessageImpl =>
          val lineFromMessage = message.getLine - 1
          val column = (message.getColumn - 1).max(0)
          if (lineFromMessage < 0)
            return None

          val lineTextFromMessage = lineText(message.getMessage)

          val document = editor.getDocument

          //todo: dotc and scalac report different lines in their messages :(
          val actualLine =
            Seq(lineFromMessage, lineFromMessage - 1, lineFromMessage + 1)
              .find { lineNumber =>
                documentLine(document, lineNumber).contains(lineTextFromMessage)
              }

          actualLine.map(line => document.getLineStartOffset(line) + column)
        case _ =>
          None
      }
    }

    override def virtualFile(t: CompilerMessage): VirtualFile = t.getVirtualFile

    private def lineText(messageText: String): String = {
      val lastLineSeparator = messageText.trim.lastIndexOf('\n')
      if (lastLineSeparator > 0) messageText.substring(lastLineSeparator).trim
      else ""
    }

    private def documentLine(document: Document, line: Int): Option[String] = {
      if (line >= 0 && line < document.getLineCount) {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        Some(document.getText(TextRange.create(lineStart, lineEnd)).trim)
      }
      else None
    }
  }

}