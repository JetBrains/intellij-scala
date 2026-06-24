package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.{EelPath, EelPathException}
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, EelProviderUtil}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.jps.incremental.scala.Client.{ClientMsg, PosInfo}
import org.jetbrains.jps.incremental.scala.MessageKind
import org.jetbrains.jps.incremental.scala.remote.SerializablePath
import org.jetbrains.plugins.scala.compiler.highlighting.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiFileExt}
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.nio.file.Path

//noinspection ApiStatus,UnstableApiUsage
private class UpdateCompilerGeneratedStateListener(project: Project) extends CompilerEventListener {
  private final val CompilerPluginTypePrefix = "<type>" // CompilerPlugin.TypePrefix

  private final val CompilerPluginTypeSuffix = "</type>" // CompilerPlugin.TypeSuffix

  private val eelDescriptor: EelDescriptor = EelProviderUtil.getEelDescriptor(project)

  private def parseEelPath(source: SerializablePath): Option[Path] =
    try {
      val pathString = FileUtil.toSystemIndependentName(SerializablePath.unsafePathAsString(source))
      val eel = EelPath.parse(pathString, eelDescriptor)
      val nio = EelNioBridgeServiceKt.asNioPath(eel)
      Some(nio)
    } catch {
      case _: EelPathException => None
    }

  private def findVirtualFile(source: SerializablePath): Option[VirtualFile] = {
    val fromEel = parseEelPath(source).flatMap(_.toVirtualFile)
    fromEel.orElse {
      // Parsing the source file path did not succeed using eel machinery.
      // Fall back to treating the path as a local path. This is the case for scratch worksheets which
      // are always created in the local filesystem.
      val rawPath = Path.of(SerializablePath.unsafePathAsString(source))
      rawPath.toVirtualFile
    }
  }

  override def eventReceived(event: CompilerEvent): Unit = {
    val oldState = CompilerGeneratedStateManager.get(project)

    event match {
      case CompilerEvent.CompilationStarted(_, _) =>
        val newHighlightOnCompilationFinished = oldState.toHighlightingState.filesWithHighlightings
        val newState = oldState.copy(highlightOnCompilationFinished = newHighlightOnCompilationFinished)
        CompilerGeneratedStateManager.update(project, newState)
      case CompilerEvent.MessageEmitted(compilationId, _, _, ClientMsg(MessageKind.Info, text, Some(source), _, Some(begin), Some(end), _)) if text.startsWith(CompilerPluginTypePrefix) =>
        findVirtualFile(source).foreach { virtualFile =>
          val tpe = text.substring(CompilerPluginTypePrefix.length, text.indexOf(CompilerPluginTypeSuffix).ensuring(_ != -1))
          val fileState = FileCompilerGeneratedState(compilationId, Set.empty, Map(((begin, end), tpe)))
          val newState = replaceOrAppendFileState(oldState, virtualFile, fileState)
          CompilerGeneratedStateManager.update(project, newState)
        }
      case CompilerEvent.MessageEmitted(compilationId, _, _, msg) =>
        for {
          text <- Option(msg.text)
          source <- msg.source
          virtualFile <- findVirtualFile(source)
        } {
          def calculateRangeInfo(startInfo: Option[PosInfo], endInfo: Option[PosInfo], debugTag: String): Option[RangeInfo] =
            for {
              startPos <- startInfo
              endPos <- endInfo if startPos != endPos
            } yield RangeInfo.Range(startPos, endPos, debugTag)

          def pointerOrProblemStart(msg: ClientMsg): Option[PosInfo] =
            msg.pointer.orElse(msg.problemStart)

          val highlightingType = kindToHighlightInfoType(msg.kind, text, virtualFile)
          val rangeInfo = (highlightingType match {
            case HighlightInfoType.WRONG_REF =>
              if (msg.pointer.isEmpty) {
                // Special handling of WRONG_REF in BSP projects.
                val pointer = msg.problemEnd.flatMap {
                  case PosInfo(line, column) if column > 0 => Some(PosInfo(line, column - 1))
                  case _ => None
                }
                pointer.map(RangeInfo.Pointer)
                  .orElse {
                    // fall back to the regular highlighting behaviour for WRONG_REF
                    calculateRangeInfo(pointerOrProblemStart(msg), msg.problemEnd, s"bsp wrong_ref fallback case, msg=$msg")
                  }
              } else {
                // Wrong reference errors are usually highlighted starting from the pointer provided by the compiler.
                // Empirically, this only highlights the name of the symbol which cannot be resolved.
                calculateRangeInfo(pointerOrProblemStart(msg), msg.problemEnd, s"wrong_ref case, msg=$msg")
              }
            case _ if ScalaHighlightingMode.useCompilerRanges =>
              // If the setting is checked, the full text range provided by the compiler is used.
              calculateRangeInfo(msg.problemStart, msg.problemEnd, s"use compiler ranges true case, msg=$msg")
            case _ =>
              // Otherwise, the range from the pointer to the end is used, matching the behaviour before
              // SCL-21339, SCL-21292 were implemented.
              calculateRangeInfo(pointerOrProblemStart(msg), msg.problemEnd, s"default case, msg=$msg")
          }).orElse(pointerOrProblemStart(msg).map(RangeInfo.Pointer))
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
          virtualFile <- findVirtualFile(source)
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
    import org.jetbrains.plugins.scala.compiler.highlighting.{HighlightInfoType => HIT}
    val scalacOptions = scalacOptionsForFile(virtualFile)
    CompilerMessageKinds.highlightInfoType(
      kind = kind,
      text = text,
      fatalWarningsFlag = CompilerOptions.containsFatalWarnings(scalacOptions),
      unusedImportsFlag = CompilerOptions.containsUnusedImports(scalacOptions)
    ) match {
      case HIT.WrongRef => HighlightInfoType.WRONG_REF
      case HIT.Error => HighlightInfoType.ERROR
      case HIT.UnusedSymbol => HighlightInfoType.UNUSED_SYMBOL
      case HIT.Warning => HighlightInfoType.WARNING
      case HIT.WeakWarning => HighlightInfoType.WEAK_WARNING
      case HIT.Information => HighlightInfoType.INFORMATION
    }
  }

  private def scalacOptionsForFile(virtualFile: VirtualFile): Seq[String] = {
    val psiFileOpt = findPsiFile(virtualFile)
    val moduleOpt = psiFileOpt.flatMap(_.module)
    val compilerSettingsOpt = psiFileOpt.flatMap(ScalaCompilerSettings.forFile)

    val optionsOpt = for {
      settings <- compilerSettingsOpt
      module <- moduleOpt
    } yield settings.getOptionsAsStrings(module.hasScala3)

    optionsOpt.getOrElse(Seq.empty)
  }

  @RequiresBackgroundThread
  private def findPsiFile(virtualFile: VirtualFile): Option[PsiFile] =
    ReadAction
      .nonBlocking[Option[PsiFile]](() => Option(PsiManager.getInstance(project).findFile(virtualFile)))
      .inSmartMode(project)
      .expireWhen(() => project.isDisposed)
      .executeSynchronously()

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
