package org.jetbrains.plugins.scala
package codeInspection.annotations

import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import com.intellij.codeInspection.{ProblemHighlightType, ProblemDescriptor, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * Nikolay.Tropin
 * 10/18/13
 */
class PrivateBeanPropertyInspection extends AbstractInspection{
  private def isBeanPropertyAnnotation(annot: ScAnnotation) = {
    val text = annot.getText
    text == "@BeanProperty" || text == "@BooleanBeanProperty"
  }

  private val description = "Bean property should not be private"
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case annot: ScAnnotation if isBeanPropertyAnnotation(annot) =>
      val member = PsiTreeUtil.getParentOfType(annot, classOf[ScMember])
      def registerProblem() = {
        val toPublicFix = new MakeNonPrivateQuickFix(member, isProtected = false, "Make field public")
        val toProtectedFix = new MakeNonPrivateQuickFix(member, isProtected = true, "Make field protected")
        val privateMod = member.getModifierList.accessModifier.get
        holder.registerProblem(privateMod, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, toPublicFix, toProtectedFix)
      }
      member match {
        case v: ScVariable if v.isPrivate => registerProblem()
        case v: ScValue if v.isPrivate => registerProblem()
        case _ =>
      }
  }
}

private class MakeNonPrivateQuickFix(member: ScModifierListOwner, isProtected: Boolean, hint: String) extends AbstractFix(hint, member){
  def doApplyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    member.setModifierProperty("private", value = false)
    if (isProtected) member.setModifierProperty("protected", value = true)
    CodeStyleManager.getInstance(member.getProject).adjustLineIndent(member.getContainingFile, member.getModifierList.getTextRange.getStartOffset)
  }
}