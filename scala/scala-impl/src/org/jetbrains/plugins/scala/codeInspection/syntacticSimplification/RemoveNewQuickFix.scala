package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
  * mattfowler
  * 5/7/16.
  */
class RemoveNewQuickFix(param: ScNewTemplateDefinition) extends AbstractFixOnPsiElement(ScalaBundle.message("new.on.case.class.instantiation.redundant"), param) {

  override protected def doApplyFix(p: ScNewTemplateDefinition)
                                   (implicit project: Project): Unit = {
    p.findFirstChildByType(ScalaTokenTypes.kNEW).delete()
    p.replaceExpression(createExpressionFromText(p.getText), removeParenthesis = false)
  }
}
