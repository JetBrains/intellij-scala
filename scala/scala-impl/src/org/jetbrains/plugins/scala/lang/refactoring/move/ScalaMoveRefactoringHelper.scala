package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringHelper
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import java.util

class ScalaMoveRefactoringHelper extends RefactoringHelper[Unit] {

  override def prepareOperation(usages: Array[UsageInfo]): Unit = {
    def priority(usageInfo: UsageInfo) = usageInfo match {
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

    util.Arrays.sort(usages, (o1: UsageInfo, o2: UsageInfo) => priority(o1) - priority(o2))
  }

  override def performOperation(project: Project, operationData: Unit): Unit = {}
}
