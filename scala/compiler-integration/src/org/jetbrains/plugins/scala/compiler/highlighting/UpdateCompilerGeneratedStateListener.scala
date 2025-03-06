package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.Client.{ClientMsg, PosInfo}
import org.jetbrains.jps.incremental.scala.MessageKind
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

private class UpdateCompilerGeneratedStateListener(project: Project) extends CompilerEventListener {
  private final val CompilerPluginTypePrefix = "<type>" // CompilerPlugin.TypePrefix

  private final val CompilerPluginTypeSuffix = "</type>" // CompilerPlugin.TypeSuffix

  override def eventReceived(event: CompilerEvent): Unit = {
    val oldState = CompilerGeneratedStateManager.get(project)

    event match {
      case CompilerEvent.CompilationStarted(_, _) =>
        val newHighlightOnCompilationFinished = oldState.toHighlightingState.filesWithHighlightings
        val newState = oldState.copy(highlightOnCompilationFinished = newHighlightOnCompilationFinished)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.MessageEmitted(compilationId, _, _, ClientMsg(MessageKind.Info, text, Some(source), _, Some(begin), Some(end), _)) if text.startsWith(CompilerPluginTypePrefix) =>
        val virtualFile = source.toPath.toVirtualFile.get
        val tpe = text.substring(CompilerPluginTypePrefix.length, text.indexOf(CompilerPluginTypeSuffix).ensuring(_ != -1))
        val fileState = FileCompilerGeneratedState(compilationId, Set.empty, Map(((begin, end), tpe)))
        val newState = replaceOrAppendFileState(oldState, virtualFile, fileState)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.MessageEmitted(compilationId, _, _, msg) =>
        for {
          text <- Option(msg.text)
          source <- msg.source
          virtualFile <- source.toPath.toVirtualFile
        } {
          def calculateRangeInfo(startInfo: Option[PosInfo], endInfo: Option[PosInfo], debugTag: String): Option[RangeInfo] =
            for {
              startPos <- startInfo
              endPos <- endInfo if startPos != endPos
            } yield RangeInfo.Range(startPos, endPos, debugTag)

          val highlightingType = kindToHighlightInfoType(msg.kind, text, virtualFile)
          val rangeInfo = (highlightingType match {
            case HighlightInfoType.WRONG_REF =>
              // Wrong reference errors are always highlighted starting from the pointer provided by the compiler.
              // Empirically, this only highlights the name of the symbol which cannot be resolved.
              calculateRangeInfo(msg.pointer, msg.problemEnd, s"wrong_ref case start=msg.pointer, end=msg.problemEnd, msg=$msg")
            case _ if ScalaProjectSettings.in(project).isUseCompilerRanges =>
              // If the setting is checked, the full text range provided by the compiler is used.
              calculateRangeInfo(msg.problemStart, msg.problemEnd, s"use compiler ranges true case start=msg.problemStart, end=msg.problemEnd, msg=$msg")
            case _ =>
              // Otherwise, the range from the pointer to the end is used, matching the behaviour before
              // SCL-21339, SCL-21292 were implemented.
              calculateRangeInfo(msg.pointer, msg.problemEnd, s"default case start=msg.pointer, end=msg.problemEnd, msg=$msg")
          }).orElse(msg.pointer.map(RangeInfo.Pointer))
          val highlighting = ExternalHighlighting(
            highlightType = highlightingType,
            message = text,
            rangeInfo = rangeInfo,
            diagnostics = msg.diagnostics
          )
          val fileState = FileCompilerGeneratedState(compilationId, Set(highlighting), Map.empty)
          val newState = replaceOrAppendFileState(oldState, virtualFile, fileState)

          CompilerGeneratedStateManager.update(project, newState)
        }
      case CompilerEvent.ProgressEmitted(_, _, progress) =>
        val newState = oldState.copy(progress = progress)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.CompilationFinished(compilationId, _, sources) =>
        val vFiles = for {
          source <- sources
          virtualFile <- source.toPath.toVirtualFile
        } yield virtualFile
        val emptyState = FileCompilerGeneratedState(compilationId, Set.empty, Map.empty)
        val intermediateState = vFiles.foldLeft(oldState) { case (acc, file) =>
          replaceOrAppendFileState(acc, file, emptyState)
        }
        val toHighlight = intermediateState.highlightOnCompilationFinished
        // Do not hold highlighting information for invalid virtual files, such as deleted ones.
        val newState = CompilerGeneratedState(
          files = intermediateState.files.filter(_._1.isValid),
          progress = 1.0,
          highlightOnCompilationFinished = Set.empty
        )

        CompilerGeneratedStateManager.update(project, newState)

        if (toHighlight.nonEmpty) {
          executeOnBackgroundThreadInNotDisposed(project) {
            val highlightingState = newState.toHighlightingState
            try {
              ExternalHighlightersService.instance(project).applyHighlightingState(toHighlight, highlightingState, compilationId)
            } catch {
              // don't know what else we can do if compilation was cancelled at this stage
              // probably just don't show updated highlightings
              case _: ProcessCanceledException =>
                // ignore
            }
          }
        }
      case _ =>
    }
  }

  private def kindToHighlightInfoType(kind: MessageKind, text: String, virtualFile: VirtualFile): HighlightInfoType = {
    val scalacOptions = scalacOptionsForFile(virtualFile)
    CompilerMessageKinds.highlightInfoType(kind, text, scalacOptions)
  }

  private def scalacOptionsForFile(virtualFile: VirtualFile): Seq[String] = {
    val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
    CompilerOptions.scalacOptions(module)
  }

  private def replaceOrAppendFileState(oldState: CompilerGeneratedState,
                                       file: VirtualFile,
                                       fileState: FileCompilerGeneratedState): CompilerGeneratedState = {
    val newFileState = oldState.files.get(file) match {
      case Some(oldFileState) if oldFileState.compilationId == fileState.compilationId =>
        oldFileState.withExtraHighlightings(fileState.highlightings).withExtraTypes(fileState.types)
      case _ =>
        fileState
    }
    val newFileStates = oldState.files.updated(file, newFileState)
    val newToHighlight = oldState.highlightOnCompilationFinished + file
    oldState.copy(files = newFileStates, highlightOnCompilationFinished = newToHighlight)
  }
}
