package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.lang3.StringUtils
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.jps.incremental.scala.MessageKind
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

private class UpdateCompilerGeneratedStateListener(project: Project) extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit = {
    val oldState = CompilerGeneratedStateManager.get(project)

    event match {
      case CompilerEvent.CompilationStarted(_, _) =>
        val newHighlightOnCompilationFinished = oldState.toHighlightingState.filesWithHighlightings
        val newState = oldState.copy(highlightOnCompilationFinished = newHighlightOnCompilationFinished)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.MessageEmitted(compilationId, _, _, msg) =>
        for {
          text <- Option(msg.text)
          source <- msg.source
          virtualFile <- source.toVirtualFile
        } {
          def calculateRangeInfo(startInfo: Option[PosInfo], endInfo: Option[PosInfo]): Option[RangeInfo] =
            for {
              startPos <- startInfo
              endPos <- endInfo if startPos != endPos
            } yield RangeInfo.Range(startPos, endPos)

          val highlightingType = kindToHighlightInfoType(msg.kind, text, virtualFile)
          val rangeInfo = (highlightingType match {
            case HighlightInfoType.WRONG_REF =>
              // Wrong reference errors are always highlighted starting from the pointer provided by the compiler.
              // Empirically, this only highlights the name of the symbol which cannot be resolved.
              calculateRangeInfo(msg.pointer, msg.problemEnd)
            case _ if ScalaProjectSettings.in(project).isUseCompilerRanges =>
              // If the setting is checked, the full text range provided by the compiler is used.
              calculateRangeInfo(msg.problemStart, msg.problemEnd)
            case _ =>
              // Otherwise, the range from the pointer to the end is used, matching the behaviour before
              // SCL-21339, SCL-21292 were implemented.
              calculateRangeInfo(msg.pointer, msg.problemEnd)
          }).orElse(msg.pointer.map(RangeInfo.Pointer))
          val highlighting = ExternalHighlighting(
            highlightType = highlightingType,
            message = text,
            rangeInfo = rangeInfo
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
              ExternalHighlightersService.instance(project).applyHighlightingState(toHighlight, highlightingState)
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

  private def kindToHighlightInfoType(kind: MessageKind, text: String, virtualFile: VirtualFile): HighlightInfoType = kind match {
    case MessageKind.Error if isErrorMessageAboutWrongRef(text) =>
      HighlightInfoType.WRONG_REF
    case MessageKind.Error if isUnusedImportMessage(text) =>
      val scalacOptions = scalacOptionsForFile(virtualFile)
      val fatalWarnings = CompilerOptions.containsFatalWarnings(scalacOptions)
      val unusedImports = CompilerOptions.containsUnusedImports(scalacOptions)
      if (fatalWarnings && unusedImports) {
        // The project has enabled fatal warnings and unused imports. We need to report an error here.
        HighlightInfoType.ERROR
      } else {
        // In all other cases, report an unused symbol instead of an error.
        // The else case contains the following cases:
        //   1. fatalWarnings && !unusedImports (silent unused imports added by us) => UNUSED_SYMBOL
        //   2. !fatalWarnings && unusedImports (cannot happen, because the unused import will be a warning, not an error)
        //   3. !fatalWarnings && !unusedImports (silent unused imports added by us) => UNUSED_SYMBOL
        HighlightInfoType.UNUSED_SYMBOL
      }
    case MessageKind.Error =>
      HighlightInfoType.ERROR
    case MessageKind.Warning if isUnusedImportMessage(text) =>
      val scalacOptions = scalacOptionsForFile(virtualFile)
      val unusedImports = CompilerOptions.containsUnusedImports(scalacOptions)
      if (unusedImports) {
        // The project has enabled unused imports. Keep the warning.
        HighlightInfoType.WARNING
      } else {
        // Silent unused imports added by us.
        HighlightInfoType.UNUSED_SYMBOL
      }
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

  private def isUnusedImportMessage(text: String): Boolean = {
    val description = CompilerMessages.description(text)
    CompilerMessages.isUnusedImport(description)
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
        oldFileState.withExtraHighlightings(fileState.highlightings)
      case _ =>
        fileState
    }
    val newFileStates = oldState.files.updated(file, newFileState)
    val newToHighlight = oldState.highlightOnCompilationFinished + file
    oldState.copy(files = newFileStates, highlightOnCompilationFinished = newToHighlight)
  }
}
