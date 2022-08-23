package org.jetbrains.plugins.scala.codeInspection.specs2

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText

class BuiltinMatcherExistsInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case elem@ScMethodCall(matcher, Seq(ScMethodCall(inner, _))) if equalToMatcher(matcher) && optional(inner) =>
      suggestFixForOptional(elem, holder)
    case elem@ScMethodCall(matcher, Seq(inner: ScExpression)) if equalToMatcher(matcher) && optional(inner) =>
      suggestFixForOptional(elem, holder)
    case elem@ScMethodCall(matcher, Seq(ScMethodCall(inner, _))) if equalToMatcher(matcher) && either(inner) =>
      suggestFixForEither(elem, holder)
    case elem@ScInfixExpr(_, op, ScMethodCall(inner, _)) if equalToOperator(op) && optional(inner) =>
      suggestFixForMustOptional(elem, holder)
    case elem@ScInfixExpr(_, op, ScMethodCall(inner, _)) if equalToOperator(op) && either(inner) =>
      suggestFixForMustEither(elem, holder)
    case elem@ScInfixExpr(_, op, inner: ScExpression) if equalToOperator(op) && optional(inner) =>
      suggestFixForMustOptional(elem, holder)
    case _ =>
  }

  private def suggestFixForOptional(elem: PsiElement, holder: ProblemsHolder): Unit =
    holder.registerProblem(elem, ScalaInspectionBundle.message("specs2.use.builtin.matcher"),
      new ReplaceWithBeSomeOrNoneQuickFix(elem))

  private def suggestFixForMustOptional(elem: PsiElement, holder: ProblemsHolder): Unit =
    holder.registerProblem(elem, ScalaInspectionBundle.message("specs2.use.builtin.matcher"),
      new ReplaceMustWithBeSomeOrNoneQuickFix(elem))

  private def suggestFixForEither(elem: PsiElement, holder: ProblemsHolder): Unit =
    holder.registerProblem(elem, ScalaInspectionBundle.message("specs2.use.builtin.matcher"),
      new ReplaceWithBeLeftOrRightQuickFix(elem))

  private def suggestFixForMustEither(elem: PsiElement, holder: ProblemsHolder): Unit =
    holder.registerProblem(elem, ScalaInspectionBundle.message("specs2.use.builtin.matcher"),
      new ReplaceWithMustBeLeftOrRightQuickFix(elem))

  private val EqualToMatchers = Seq("be_===", "be_==", "beEqualTo", "equalTo", "beTypedEqualTo", "typedEqualTo")
  private val EqualToOperators = Seq("must_===", "must_==", "mustEqual")

  private def equalToMatcher(expr: ScExpression) = EqualToMatchers.contains(expr.getText)

  private def equalToOperator(expr: ScExpression) = EqualToOperators.contains(expr.getText)

  private def optional(expr: ScExpression) = expr.textMatches("Some") || expr.textMatches("None")

  private def either(expr: ScExpression) = expr.textMatches("Left") || expr.textMatches("Right")

  private class ReplaceWithBeSomeOrNoneQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    override protected def doApplyFix(element: PsiElement)
                                     (implicit project: Project): Unit = element match {
      case ScMethodCall(_, Seq(ScMethodCall(ElementText("Some"), Seq(ElementText(value))))) =>
        repair(s"beSome($value)", element)
      case ScMethodCall(_, Seq(ElementText("None"))) =>
        repair(s"beNone", element)
    }
  }

  private class ReplaceMustWithBeSomeOrNoneQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    override protected def doApplyFix(element: PsiElement)
                                     (implicit project: Project): Unit = element match {
      case ScInfixExpr(ElementText(text), _, ScMethodCall(ElementText("Some"), Seq(ElementText(value)))) =>
        repair(s"$text must beSome($value)", element)
      case ScInfixExpr(ElementText(text), _, ElementText("None")) =>
        repair(s"$text must beNone", element)
    }
  }

  private class ReplaceWithBeLeftOrRightQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    override protected def doApplyFix(element: PsiElement)
                                     (implicit project: Project): Unit = element match {
      case ScMethodCall(_, Seq(ScMethodCall(ElementText(option), Seq(ElementText(value))))) =>
        repair(s"be$option($value)", element)
    }
  }

  private class ReplaceWithMustBeLeftOrRightQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    override protected def doApplyFix(element: PsiElement)
                                     (implicit project: Project): Unit = element match {
      case ScInfixExpr(ElementText(text), _, ScMethodCall(ElementText(option), Seq(ElementText(value)))) =>
        repair(s"$text must be$option($value)", element)
    }
  }

  private def repair(fixed: String, expr: PsiElement) = {
    val retStmt = createExpressionWithContextFromText(fixed, expr.getContext, expr)
    expr.replace(retStmt)
  }
}
