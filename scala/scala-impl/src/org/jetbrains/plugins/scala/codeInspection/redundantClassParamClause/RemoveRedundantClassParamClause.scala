package org.jetbrains.plugins.scala.codeInspection.redundantClassParamClause

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters

class RemoveRedundantClassParamClause(params: ScParameters)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("remove.redundant.parameter.clause"), params) {

  override protected def doApplyFix(p: ScParameters)
                                   (implicit project: Project): Unit = {
    // do not delete the ScParameters itself, because they are expected to exist in the psi tree
    p.deleteChildRange(p.getFirstChild, p.getLastChild)
  }
}
