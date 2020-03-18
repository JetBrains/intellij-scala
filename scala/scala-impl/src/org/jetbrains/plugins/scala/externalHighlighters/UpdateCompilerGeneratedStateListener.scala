package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import CompilerGeneratedStateManager.FileCompilerGeneratedState
import org.jetbrains.plugins.scala.project.template.FileExt

private class UpdateCompilerGeneratedStateListener(project: Project)
  extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit =
    Option(event).flatMap {
      case CompilerEvent.MessageEmitted(compilationId, msg) =>
        for {
          text <- Option(msg.text)
          line <- msg.line
          column <- msg.column
          source <- msg.source
          virtualFile <- source.toVirtualFile
          highlighting = ExternalHighlighting(
            severity = kindToSeverity(msg.kind),
            message = text,
            fromLine = line.toInt,
            fromColumn = column.toInt,
            toLine = None,
            toColumn = None
          )
        } yield virtualFile -> FileCompilerGeneratedState(compilationId, Set(highlighting))
      case CompilerEvent.RangeMessageEmitted(compilationId, msg) =>
        for {
          virtualFile <- msg.source.toVirtualFile
          highlighting = ExternalHighlighting(
            severity = msg.severity,
            message = msg.text,
            fromLine = msg.fromLine,
            fromColumn = msg.fromColumn,
            toLine = msg.toLine,
            toColumn = msg.toColumn
          )
        } yield virtualFile -> FileCompilerGeneratedState(compilationId, Set(highlighting))
      case CompilerEvent.CompilationFinished(compilationId, source) =>
        source.toVirtualFile.map { virtualFile =>
          virtualFile -> FileCompilerGeneratedState(compilationId, Set.empty)
        }
      case _ =>
        None
    }.foreach { case (virtualFile, fileState) =>
      replaceOrAppend(virtualFile, fileState)
    }

  private def kindToSeverity(kind: Kind): HighlightSeverity = kind match {
    case Kind.ERROR => HighlightSeverity.ERROR
    case Kind.WARNING => HighlightSeverity.WARNING
    case _ => HighlightSeverity.INFORMATION
  }

  private def replaceOrAppend(file: VirtualFile, fileState: FileCompilerGeneratedState): Unit = {
    val oldState = CompilerGeneratedStateManager.get(project)
    val newFileState = oldState.get(file) match {
      case None =>
        fileState
      case Some(oldFileState) if oldFileState.compilationId != fileState.compilationId =>
        fileState
      case Some(oldFileState) =>
        oldFileState.copy(
          highlightings = oldFileState.highlightings ++ fileState.highlightings
        )
    }
    val newState = oldState.updated(file, newFileState)
    CompilerGeneratedStateManager.update(project, newState)
  }
}
