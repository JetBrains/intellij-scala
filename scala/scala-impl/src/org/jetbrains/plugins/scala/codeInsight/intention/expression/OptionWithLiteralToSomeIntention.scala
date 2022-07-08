package org.jetbrains.plugins.scala.codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.{`scalaOption`, literal}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class OptionWithLiteralToSomeIntention extends PsiElementBaseIntentionAction  {
  import OptionWithLiteralToSomeIntention._

  override def getFamilyName: String = familyName

  override def getText: String = ScalaInspectionBundle.message("replace.with.some")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = element match {
    case OptionLiteral(_, _) => true
    case _ => false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = element match {
    case OptionLiteral(opt, constant) =>
      val newExpr = ScalaPsiElementFactory.createExpressionFromText(s"Some($constant)")(project)
      opt.replaceExpression(newExpr, removeParenthesis = true)
    case _ =>
  }
}

object OptionWithLiteralToSomeIntention {

  val familyName: String = ScalaInspectionBundle.message("replace.option.with.some")

  object OptionLiteral {
    def unapply(element: PsiElement): Option[(ScMethodCall, String)] =  {
      PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall]) match {
        case opt @ `scalaOption`(literal(constant)) if constant != "null" => Some((opt, constant))
        case _ => None
      }
    }
  }
}
