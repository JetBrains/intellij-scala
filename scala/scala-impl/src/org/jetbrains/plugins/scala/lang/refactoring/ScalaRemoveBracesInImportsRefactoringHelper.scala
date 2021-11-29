package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringHelper
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.refactoring.ScalaRemoveBracesInImportsRefactoringHelper._

/**
 * Inspired by [[com.intellij.refactoring.OptimizeImportsRefactoringHelper]]
 *
 * NOTE: our implementation differs from Java!!!
 * In Java they run full import optimisation for files, where usages were found (same in Kotlin, BTW).
 * In Scala I am afraid to run full import optimisation, cause for now it's quite buggy and might produce some wrong code.
 */
final class ScalaRemoveBracesInImportsRefactoringHelper extends RefactoringHelper[MyData] {

  override def prepareOperation(usages: Array[UsageInfo]): MyData = {
    val importStatements: Set[ScImportExpr] = usages.iterator
      .filterNot(_.isNonCodeUsage)
      .flatMap { usage =>
        usage.getElement match {
          case null =>
            None
          case element =>
            val importExpr = ScalaPsiUtil.getParentOfTypeInsideImport(element, classOf[ScImportExpr], strict = false)
            Option(importExpr)
        }
      }
      .toSet

    new MyData(importStatements)
  }

  override def performOperation(project: Project, operationData: MyData): Unit =
    inWriteAction {
      //NOTE: we could wrap the operation into `SequentialModalProgressTask` as it's done in
      //com.intellij.refactoring.OptimizeImportsRefactoringHelper
      //but looks like `deleteRedundantSingleSelectorBraces` shouldn't delete that much time
      //If we observe any freeze reports in this refactoring, wrap this into a progress task
      val statements = operationData.importStatements
      statements.foreach { scImport: ScImportExpr =>
        scImport.deleteRedundantSingleSelectorBraces()
      }
    }
}

object ScalaRemoveBracesInImportsRefactoringHelper {
  final class MyData(val importStatements: Set[ScImportExpr])
}