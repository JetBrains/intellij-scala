package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.lang3.StringUtils
import org.jetbrains.jps.incremental.scala.MessageKind
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.{Pos, PosRange}
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.project.template.FileExt

private class UpdateCompilerGeneratedStateListener(project: Project) extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit = {
    val oldState = CompilerGeneratedStateManager.get(project)

    event match {
      case CompilerEvent.CompilationStarted(_, _) =>
        val newHighlightOnCompilationFinished = oldState.toHighlightingState.filesWithHighlightings
        val newState = oldState.copy(highlightOnCompilationFinished = newHighlightOnCompilationFinished)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.MessageEmitted(compilationId, _, msg) =>
        for {
          text <- Option(msg.text)
          source <- msg.source
          virtualFile <- source.toVirtualFile
        } {
          val fromOpt = Pos.fromPosInfo(msg.from)
          val rangeOpt = fromOpt.map { fromPos =>
            val toPos = Pos.fromPosInfo(msg.to).getOrElse(fromPos)
            PosRange(fromPos, toPos)
          }
          val highlighting = ExternalHighlighting(
            highlightType = kindToHighlightInfoType(msg.kind, text),
            message = text,
            rangeOpt
          )
          val fileState = FileCompilerGeneratedState(compilationId, Set(highlighting))
          val newState = replaceOrAppendFileState(oldState, virtualFile, fileState)

          CompilerGeneratedStateManager.update(project, newState)
        }
      case CompilerEvent.ProgressEmitted(_, _, progress) =>
        val newState = oldState.copy(progress = progress)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.CompilationFinished(compilationId, _, sources) =>
        val vFiles = for {
          source <- sources
          virtualFile <- source.toVirtualFile
        } yield virtualFile
        val emptyState = FileCompilerGeneratedState(compilationId, Set.empty)
        val intermediateState = vFiles.foldLeft(oldState) { case (acc, file) =>
          replaceOrAppendFileState(acc, file, emptyState)
        }.copy(progress = 1.0)
        val toHighlight = intermediateState.highlightOnCompilationFinished
        val newState = intermediateState.copy(highlightOnCompilationFinished = Set.empty)

        CompilerGeneratedStateManager.update(project, newState)

        if (toHighlight.nonEmpty) {
          executeOnBackgroundThreadInNotDisposed(project) {
            val highlightingState = newState.toHighlightingState
            updateHighlightings(toHighlight, highlightingState)
            ExternalHighlighters.informWolf(project, highlightingState)
          }
        }
      case _ =>
    }
  }

  private def kindToHighlightInfoType(kind: MessageKind, text: String): HighlightInfoType = kind match {
    case MessageKind.Error if isErrorMessageAboutWrongRef(text) =>
      HighlightInfoType.WRONG_REF
    case MessageKind.Error =>
      HighlightInfoType.ERROR
    case MessageKind.Warning =>
      HighlightInfoType.WARNING
    case MessageKind.Info =>
      HighlightInfoType.WEAK_WARNING
    case _ =>
      HighlightInfoType.INFORMATION
  }

  private def isErrorMessageAboutWrongRef(text: String): Boolean =
    StringUtils.startsWithIgnoreCase(text, "value") && text.contains("is not a member of") ||
      StringUtils.startsWithIgnoreCase(text, "not found:") ||
      StringUtils.startsWithIgnoreCase(text, "cannot find symbol")

  private def replaceOrAppendFileState(oldState: CompilerGeneratedState,
                                       file: VirtualFile,
                                       fileState: FileCompilerGeneratedState): CompilerGeneratedState = {
    val newFileState = oldState.files.get(file) match {
      case Some(oldFileState) if oldFileState.compilationId == fileState.compilationId =>
        oldFileState.withExtraHighlightings(fileState.highlightings)
      case _ =>
        fileState
    }
    val newFileStates = oldState.files.updated(file, newFileState)
    val newToHighlight = oldState.highlightOnCompilationFinished + file
    oldState.copy(files = newFileStates, highlightOnCompilationFinished = newToHighlight)
  }

  private def updateHighlightings(virtualFiles: Set[VirtualFile], state: HighlightingState): Unit = try {
    val filteredVirtualFiles = ExternalHighlighters.filterFilesToHighlightBasedOnFileLevel(virtualFiles, project)
    for {
      editor <- EditorFactory.getInstance.getAllEditors
      editorProject <- Option(editor.getProject)
      if editorProject == project
      vFile <- editor.getDocument.virtualFile
      if filteredVirtualFiles contains vFile
    } ExternalHighlighters.applyHighlighting(project, editor, state)
  } catch {
    //don't know what else we can do if compilation was cancelled at this stage
    //probably just don't show updated highlightings
    case _: ProcessCanceledException =>
      //ignore
  }
}
