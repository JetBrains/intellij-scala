package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.util.{DocumentUtil, Processor}
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.caches.CachesUtil.fileModCount
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedImportPass.scheduleOnTheFlyImportOptimizer
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.{util => ju}
import scala.jdk.CollectionConverters.SeqHasAsJava

class ScalaUnusedImportPass(override val file: PsiFile, editor: Editor, override val document: Document,
                            highlightInfoProcessor: HighlightInfoProcessor)
  extends ProgressableTextEditorHighlightingPass(
    file.getProject,
    document,
    "Scala Unused Symbols",
    file,
    editor,
    if (file.getTextRange == null) throw new AssertionError(s"File text range is null: ${file.getClass}") else file.getTextRange,
    /*runIntentionPassAfter*/ false,
    highlightInfoProcessor
  ) with ScalaUnusedImportPassBase {

  override protected def getFixes: List[IntentionAction] = List(new ScalaOptimizeImportsFix, new ScalaEnableOptimizeImportsOnTheFlyFix)

  private var myHighlights: ju.List[HighlightInfo] = _
  private var myOptimizeImportsRunnable: Option[Runnable] = None

  override def collectInformationWithProgress(progress: ProgressIndicator): Unit = file match {
    case _ if HighlightingLevelManager.getInstance(file.getProject).shouldInspect(file) =>
      file.findScalaLikeFile match {
        case Some(scalaFile: ScalaFile) =>
          val unusedImports = UsageTracker.getUnusedImports(scalaFile)
          val highlightInfos = collectHighlightings(unusedImports)
          myHighlights = highlightInfos.toList.asJava

          if (ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
            myOptimizeImportsRunnable = Some(new ScalaImportOptimizer(isOnTheFly = true).processFile(scalaFile, progress))
          }
        case _=>
      }
    case _: ScalaFile => myHighlights = ju.Collections.emptyList()
    case _ =>
  }

  override def applyInformationWithProgress(): Unit = {
    if (myHighlights == null) return

    implicit val project: Project = file.getProject
    highlightAll(myHighlights)
    ScalaUnusedImportPass.markFileUpToDate(file)

    if (editor != null && !myHighlights.isEmpty)
      myOptimizeImportsRunnable.foreach(scheduleOnTheFlyImportOptimizer(file, _))
  }
}

object ScalaUnusedImportPass {
  private val SCALA_LAST_POST_PASS_TIMESTAMP = Key.create[java.lang.Long]("SCALA_LAST_POST_PASS_TIMESTAMP")

  def scheduleOnTheFlyImportOptimizer(file: PsiFile, runnable: Runnable): Unit = {
    val project: Project = file.getProject
    val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document != null &&
        ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY &&
        file.isWritable &&
        ScalaUnusedImportPass.timeToOptimizeImports(file)
    ) {
      val stamp: Long = document.getModificationStamp

      invokeLater {
        if (!project.isDisposed &&
          document.getModificationStamp == stamp &&
          !UndoManager.getInstance(project).isUndoInProgress &&
          !UndoManager.getInstance(project).isRedoInProgress
        ) {
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          DocumentUtil.writeInRunUndoTransparentAction(runnable)
        }
      }
    }
  }

  private[codeInspection] def isUpToDate(file: PsiFile) =
    file.getUserData(SCALA_LAST_POST_PASS_TIMESTAMP) match {
      case null => false
      case lastStamp => lastStamp == fileModCount(file)
    }

  private def markFileUpToDate(file: PsiFile): Unit =
    file.putUserData(SCALA_LAST_POST_PASS_TIMESTAMP, java.lang.Long.valueOf(fileModCount(file)))

  private def timeToOptimizeImports(file: PsiFile): Boolean =
    ScalaApplicationSettings.getInstance.OPTIMIZE_IMPORTS_ON_THE_FLY && {
      val codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(file.getProject)
      file match {
        case _: ScalaFile if codeAnalyzer.isHighlightingAvailable(file) && codeAnalyzer.isErrorAnalyzingFinished(file) =>
          !containsErrorsPreventingOptimize(file) && DaemonListeners.canChangeFileSilently(file)
        case _ => false
      }
    }

  private def containsErrorsPreventingOptimize(file: PsiFile): Boolean =
    PsiDocumentManager.getInstance(file.getProject).getDocument(file) match {
      case null => true
      case document: Document =>
        !DaemonCodeAnalyzerEx.processHighlights(
          document,
          file.getProject,
          HighlightSeverity.ERROR,
          0,
          document.getTextLength,
          ((_: HighlightInfo) => false) : Processor[_ >: HighlightInfo] //todo: only unresolved ref issues?
        )

    }
}
