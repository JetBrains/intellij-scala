package org.jetbrains.plugins.scala.codeInspection.caseClassRedundantNew

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * mattfowler
  * 5/7/16.
  */
class RemoveNewQuickFix(param: ScNewTemplateDefinition) extends AbstractFixOnPsiElement(ScalaBundle.message("new.on.case.class.instantiation.redundant"), param) {
  override def doApplyFix(project: Project): Unit = {
    val p = getElement
    if (!p.isValid) return
    p.findFirstChildByType(ScalaTokenTypes.kNEW).delete()
    p.replaceExpression(ScalaPsiElementFactory.createExpressionFromText(param.getText, p.getManager), false)
  }
}
