package org.jetbrains.plugins.scala
package annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.MakeNonPrivateQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * Nikolay.Tropin
 * 11/6/13
 */
object PrivateBeanProperty extends AnnotatorPart[ScAnnotation] {
  def annotate(element: ScAnnotation, holder: AnnotationHolder, typeAware: Boolean = false): Unit = {
    if (!isBeanPropertyAnnotation(element)) return
    val member = PsiTreeUtil.getParentOfType(element, classOf[ScMember])
    def registerProblem() = {
      val toPublicFix = new MakeNonPrivateQuickFix(member, toProtected = false)
      val toProtectedFix = new MakeNonPrivateQuickFix(member, toProtected = true)
      val privateMod = member.getModifierList.accessModifier.get
      val errorAnnotation = holder.createErrorAnnotation(privateMod, "Bean property should not be private")
      errorAnnotation.registerFix(toPublicFix)
      errorAnnotation.registerFix(toProtectedFix)
    }
    member match {
      case v: ScVariable if v.isPrivate => registerProblem()
      case v: ScValue if v.isPrivate => registerProblem()
      case _ =>
    }
  }

  private def isBeanPropertyAnnotation(annot: ScAnnotation) = {
    val text = annot.getText
    text == "@BeanProperty" || text == "@BooleanBeanProperty"
  }
}
