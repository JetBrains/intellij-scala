package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.externalHighlighters.HighlightingStateManager.FileHighlightingState

private class HighlightingUpdatingCompilerEventListener(project: Project)
  extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit =
    Option(event).flatMap {
      case CompilerEvent.MessageEmitted(compilationId, msg) =>
        for {
          text <- Option(msg.text)
          line <- msg.line
          column <- msg.column
          source <- msg.source
          virtualFile <- findVirtualFile(source)
          highlighting = ExternalHighlighting(
            severity = kindToSeverity(msg.kind),
            message = text,
            fromLine = line.toInt,
            fromColumn = column.toInt,
            toLine = None,
            toColumn = None
          )
          fileState = FileHighlightingState(compilationId, Set(highlighting))
        } yield virtualFile -> fileState

      case CompilerEvent.RangeMessageEmitted(compilationId, msg) =>
        val highlighting = ExternalHighlighting(msg.severity, msg.text, msg.fromLine, msg.fromColumn, msg.toLine, msg. toColumn)
        val virtualFile = findVirtualFile(msg.source)
        val fileState = FileHighlightingState(compilationId, Set(highlighting))
        virtualFile.map (vfile => vfile -> fileState)

      case CompilerEvent.CompilationFinished(compilationId, source) =>
        findVirtualFile(source).map { virtualFile =>
          virtualFile -> FileHighlightingState(compilationId, Set.empty)
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

  private def findVirtualFile(file: File): Option[VirtualFile] =
    Option(VirtualFileManager.getInstance.findFileByUrl(file.toPath.toUri.toString))

  private def replaceOrAppend(file: VirtualFile, fileState: FileHighlightingState): Unit = {
    val oldState = HighlightingStateManager.get(project)
    val newFileState = oldState.get(file) match {
      case None =>
        fileState
      case Some(oldFileState) if oldFileState.compilationId != fileState.compilationId =>
        fileState
      case Some(oldFileState) =>
        oldFileState.copy(highlightings = oldFileState.highlightings ++ fileState.highlightings)
    }
    val newState = oldState.updated(file, newFileState)
    HighlightingStateManager.update(project, newState)
  }
}
