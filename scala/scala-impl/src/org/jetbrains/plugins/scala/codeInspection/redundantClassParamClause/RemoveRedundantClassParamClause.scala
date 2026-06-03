package org.jetbrains.plugins.scala.codeInspection.redundantClassParamClause

import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters

final class RemoveRedundantClassParamClause(params: ScParameters)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.redundant.parameter.clause"), params)
    with DumbAware {
  override protected def doApplyFix(p: ScParameters)
                                   (implicit project: Project): Unit = {
    // do not delete the ScParameters itself, because they are expected to exist in the psi tree
    p.deleteChildRange(p.getFirstChild, p.getLastChild)
  }
}
