package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import java.util
import java.util.Collections

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.{DocumentUtil, Processor}
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2009
 */

class ScalaUnusedImportPass(val file: PsiFile, editor: Editor, val document: Document,
                            highlightInfoProcessor: HighlightInfoProcessor)
  extends ProgressableTextEditorHighlightingPass(
    file.getProject, document, "Scala Unused Symbols", file, editor,
    if (file.getTextRange == null) throw new AssertionError(s"File text range is null: ${file.getClass}") else file.getTextRange, true,
      highlightInfoProcessor) with ScalaUnusedImportPassBase {
  protected def getFixes: List[IntentionAction] = List(new ScalaOptimizeImportsFix, new ScalaEnableOptimizeImportsOnTheFlyFix)

  private var myHighlights: util.List[HighlightInfo] = _
  private var myOptimizeImportsRunnable: Runnable = _

  override def collectInformationWithProgress(progress: ProgressIndicator): Unit = {
    file match {
      case scalaFile: ScalaFile if HighlightingLevelManager.getInstance(file.getProject) shouldInspect file =>
        val unusedImports = UsageTracker.getUnusedImports(scalaFile)
        val annotations = collectAnnotations(unusedImports, new AnnotationHolderImpl(new AnnotationSession(file)))

        val list = new util.ArrayList[HighlightInfo](annotations.length)
        annotations foreach (annotation => list add (HighlightInfo fromAnnotation annotation) )

        if (ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
          myOptimizeImportsRunnable = new ScalaImportOptimizer().processFile(file, progress)
        }

        myHighlights = list
      case _: ScalaFile => myHighlights = Collections.emptyList()
      case _ =>
    }
  }

  override def applyInformationWithProgress(): Unit = {
    if (myHighlights == null) return
    highlightAll(myHighlights)
    ScalaUnusedImportPass.markFileUpToDate(file)

    if (editor != null && !myHighlights.isEmpty) {
      if (myOptimizeImportsRunnable != null &&
        ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY &&
        ScalaUnusedImportPass.timeToOptimizeImports(file) && file.isWritable) {
        ScalaUnusedImportPass.invokeOnTheFlyImportOptimizer(myOptimizeImportsRunnable, file)
      }
    }
  }
}

object ScalaUnusedImportPass {
  private val SCALA_LAST_POST_PASS_TIMESTAMP: Key[java.lang.Long] = Key.create("SCALA_LAST_POST_PASS_TIMESTAMP")
  private val LOG = Logger.getInstance(getClass)

  //todo: copy/paste from QuickFixFactoryImpl
  private def invokeOnTheFlyImportOptimizer(runnable: Runnable, file: PsiFile) {
    val project: Project = file.getProject
    val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) return
    val stamp: Long = document.getModificationStamp
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        if (project.isDisposed || document.getModificationStamp != stamp) return
        val undoManager: UndoManager = UndoManager.getInstance(project)
        if (undoManager.isUndoInProgress || undoManager.isRedoInProgress) return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        DocumentUtil.writeInRunUndoTransparentAction(runnable)
      }
    })
  }

  private[codeInspection] def isUpToDate(file: PsiFile): Boolean = {
    val lastStamp = file.getUserData(SCALA_LAST_POST_PASS_TIMESTAMP)
    val currentStamp: Long = PsiModificationTracker.SERVICE.getInstance(file.getProject).getModificationCount
    lastStamp != null && lastStamp == currentStamp || !ProblemHighlightFilter.shouldHighlightFile(file)
  }

  private def markFileUpToDate(file: PsiFile) {
    val lastStamp: java.lang.Long = PsiModificationTracker.SERVICE.getInstance(file.getProject).getModificationCount
    file.putUserData(SCALA_LAST_POST_PASS_TIMESTAMP, lastStamp)
  }

  private def timeToOptimizeImports(file: PsiFile): Boolean = {
    if (!ScalaApplicationSettings.getInstance.OPTIMIZE_IMPORTS_ON_THE_FLY) return false
    val codeAnalyzer: DaemonCodeAnalyzerEx = DaemonCodeAnalyzerEx.getInstanceEx(file.getProject)
    if (file == null || !codeAnalyzer.isHighlightingAvailable(file) || !file.isInstanceOf[ScalaFile]) return false
    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false
    val errors: Boolean = containsErrorsPreventingOptimize(file)
    !errors && DaemonListeners.canChangeFileSilently(file)
  }

  private def containsErrorsPreventingOptimize(file: PsiFile): Boolean = {
    val document: Document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
    if (document == null) return true
    val hasErrorsExceptUnresolvedImports: Boolean = !DaemonCodeAnalyzerEx.processHighlights(document, file.getProject,
      HighlightSeverity.ERROR, 0, document.getTextLength, new Processor[HighlightInfo] {
      def process(error: HighlightInfo): Boolean = false //todo: only unresolved ref issues?
    })
    hasErrorsExceptUnresolvedImports
  }
}
