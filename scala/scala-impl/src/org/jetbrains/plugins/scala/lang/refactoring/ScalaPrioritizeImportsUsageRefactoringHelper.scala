package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringHelper
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import java.util

final class ScalaPrioritizeImportsUsageRefactoringHelper extends RefactoringHelper[Unit] {

  /**
   * Move usages inside imports to the beginning of the usages.<br>
   * Scala refactoring code (i.e. "Move" refactoring) relies on the fact that imports are processed first.
   */
  override def prepareOperation(usages: Array[UsageInfo], elements: util.List[PsiElement]): Unit = {
    moveImportsToStart(usages)
    ()
  }

  private def moveImportsToStart(usages: Array[UsageInfo]): Unit = {
    util.Arrays.sort(usages, (o1: UsageInfo, o2: UsageInfo) => priority(o1) - priority(o2))
  }

  private def priority(usageInfo: UsageInfo) = usageInfo match {
    case moveUsage: MoveRenameUsageInfo =>
      moveUsage.getReference match {
        case scRef: ScReference =>
          val isImportRef = ScalaPsiUtil.getParentImportExpression(scRef) != null

          if (isImportRef) 0
          else 1
        case _ => 1
      }

    case _ => 1
  }

  override def performOperation(project: Project, operationData: Unit): Unit = {}
}
