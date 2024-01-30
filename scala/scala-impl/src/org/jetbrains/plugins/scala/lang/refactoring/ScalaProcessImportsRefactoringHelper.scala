package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.refactoring.RefactoringHelper
import com.intellij.usageView.UsageInfo
import com.intellij.util.{IncorrectOperationException, SequentialModalProgressTask, SequentialTask}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt, PsiNamedElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaProcessImportsRefactoringHelper._

import java.util
import scala.collection.mutable

/**
 * The helper processes imports after refactoring is finished (e.g. Move refactoring):
 *
 *  1. Removes unused single names in import with wildcards<br>
 *     see [[ScalaImportOptimizer.removeAllUnusedSingleNamesInImportsWithWildcards]]<br>
 *     NOTE: we do this step AFTER refactoring because this operation is not lightweight to do it
 *     when rebinding each import expression in [[org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl.bindToElement]] (see SCL-19801)
 *
 *  1. Deletes redundant braces in import expressions<br>
 *     see [[org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr.deleteRedundantSingleSelectorBraces]]
 *
 * @see [[com.intellij.refactoring.OptimizeImportsRefactoringHelper]]<br>
 *      (our implementation differs from Java and Kotlin)
 */
final class ScalaProcessImportsRefactoringHelper extends RefactoringHelper[MyData] {

  override def prepareOperation(usages: Array[UsageInfo], elements: util.List[PsiElement]): MyData = {
    val fileToImportHolders: mutable.Map[ScalaFile, mutable.Set[ScImportsHolder]] =
      mutable.HashMap.empty

    for {
      usage <- usages.iterator if !usage.isNonCodeUsage
      scalaElement <- Option(usage.getElement).filterByType[ScalaPsiElement]
      importStmt <- Option(ScalaPsiUtil.getParentOfTypeInsideImport(scalaElement, classOf[ScImportStmt], strict = false))
      importHolder <- Option(importStmt.getParent.asInstanceOf[ScImportsHolder])
      containingFile <- importHolder.containingScalaFile
    } {
      fileToImportHolders.getOrElseUpdate(containingFile, mutable.Set.empty[ScImportsHolder]) += importHolder
    }

    new MyData(fileToImportHolders.view.mapValues(_.toSet).toMap)
  }

  override def performOperation(project: Project, operationData: MyData): Unit = {
    val filesWithImportHolders = operationData.filesWitImportHolders.toSeq
    if (filesWithImportHolders.isEmpty)
      return

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled((() => {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }): Runnable)

    DumbService.getInstance(project).completeJustSubmittedTasks()

    val progressTask = new SequentialModalProgressTask(project, ScalaBundle.message("processing.imports.modified.during.refactoring"), false)
    progressTask.setMinIterationTime(200)
    progressTask.setTask(new ScalaProcessImportsAfterRefactoringTask(progressTask, filesWithImportHolders))
    ProgressManager.getInstance.run(progressTask)
  }
}

object ScalaProcessImportsRefactoringHelper {
  final class MyData(val filesWitImportHolders: Map[ScalaFile, Set[ScImportsHolder]])

  private val Log = Logger.getInstance(classOf[ScalaProcessImportsRefactoringHelper])

  private final class ScalaProcessImportsAfterRefactoringTask(
    val myTask: SequentialModalProgressTask,
    val filesWitImportHolders: Seq[(ScalaFile, Set[ScImportsHolder])]
  ) extends SequentialTask {

    private val myTotal: Int = filesWitImportHolders.size
    private var myCount: Int = 0

    private val myFiles = filesWitImportHolders.iterator

    override def isDone: Boolean = !myFiles.hasNext

    override def iteration: Boolean = {
      val (scalaFile, importHolders) = myFiles.next()

      val virtualFile = scalaFile.getVirtualFile

      val indicator: ProgressIndicator = myTask.getIndicator
      if (indicator != null) {
        val filePresentable = Option(virtualFile).map(_.getPresentableUrl).getOrElse(scalaFile.name)
        indicator.setText2(filePresentable)
        indicator.setFraction(myCount.toDouble / myTotal)

        myCount += 1
      }


      if (scalaFile.isValid) inWriteAction {
        try {
          val settings0 = OptimizeImportSettings.apply(scalaFile)

          // 1. disable sorting, it can lead to an unnecessary changelog after refactoring
          // 2. don't add wildcard imports here, it should be done only on explicit "Optimize Imports" action
          val settings = settings0
            .copy(sortImports = false)
            .withoutCollapseSelectorsToWildcard

          for {
            optimizer <- ScalaImportOptimizer.findScalaOptimizerFor(scalaFile)
            importHolder <- importHolders
          } {
            optimizer.removeAllUnusedSingleNamesInImportsWithWildcards(importHolder, settings)

            for {
              importStmt <- importHolder.getImportStatements.iterator
              importExpr <- importStmt.importExprs.iterator
            } {
              importExpr.deleteRedundantSingleSelectorBraces()
            }
          }
        } catch {
          case e: IncorrectOperationException =>
            Log.error(e)
        }
      }

      isDone
    }
  }
}
