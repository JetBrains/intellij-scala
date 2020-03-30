package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.project.template.FileExt

private class UpdateCompilerGeneratedStateListener(project: Project)
  extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit = {
    val newState = Option(event).flatMap {
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
          fileState = FileCompilerGeneratedState(compilationId, Set(highlighting))
        } yield replaceOrAppendFileState(virtualFile, fileState)
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
          fileState = FileCompilerGeneratedState(compilationId, Set(highlighting))
        } yield replaceOrAppendFileState(virtualFile, fileState)
      case CompilerEvent.CompilationFinished(compilationId, sources) =>
        val vFiles = for {
          source <- sources
          virtualFile <- source.toVirtualFile
        } yield virtualFile
        val emptyState = FileCompilerGeneratedState(compilationId, Set.empty)
        Some(replaceOrAppendFileState(vFiles, emptyState))
      case CompilerEvent.ProgressEmitted(_, progress) =>
        Some(updateProgress(progress))
      case _ =>
        None
    }

    newState.foreach { state =>
      CompilerGeneratedStateManager.update(project, state)
    }
  }

  private def kindToSeverity(kind: Kind): HighlightSeverity = kind match {
    case Kind.ERROR => HighlightSeverity.ERROR
    case Kind.WARNING => HighlightSeverity.WARNING
    case _ => HighlightSeverity.INFORMATION
  }

  private def replaceOrAppendFileState(files: Traversable[VirtualFile], fileState: FileCompilerGeneratedState): CompilerGeneratedState = {
    val oldState = CompilerGeneratedStateManager.get(project)
    val oldFileStates = oldState.files
    val newFileStates = files.foldLeft(oldFileStates) { case (acc, file) =>
      val newFileState = acc.get(file) match {
        case Some(oldFileState) if oldFileState.compilationId == fileState.compilationId =>
          oldFileState.withExtraHighlightings(fileState.highlightings)
        case _ =>
          fileState
      }
      acc.updated(file, newFileState)
    }
    oldState.copy(files = newFileStates)
  }

  private def replaceOrAppendFileState(file: VirtualFile, fileState: FileCompilerGeneratedState): CompilerGeneratedState =
    replaceOrAppendFileState(Some(file), fileState)

  private def updateProgress(progress: Double): CompilerGeneratedState =
    CompilerGeneratedStateManager.get(project).copy(progress = progress)
}
