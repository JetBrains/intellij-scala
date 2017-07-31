package org.jetbrains.plugins.scala.codeInspection.specs2

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText

class BuiltinMatcherExistsInspection
  extends AbstractInspection("Specs2Matchers",
                             InspectionBundle.message("specs2.builtin.matcher.alternative.exists")) {

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case elem @ ScMethodCall(matcher, Seq(ScMethodCall(inner, _))) if equalToMatcher(matcher) && optional(inner) =>
      suggestFixForOptional(elem, holder)
    case elem @ ScMethodCall(matcher, Seq(inner: ScExpression)) if equalToMatcher(matcher) && optional(inner) =>
      suggestFixForOptional(elem, holder)
    case elem @ ScMethodCall(matcher, Seq(ScMethodCall(inner, _))) if equalToMatcher(matcher) && either(inner) =>
      suggestFixForEither(elem, holder)
    case elem @ ScInfixExpr(_, op, ScMethodCall(inner, _)) if equalToOperator(op) && optional(inner) =>
      suggestFixForMustOptional(elem, holder)
    case elem @ ScInfixExpr(_, op, ScMethodCall(inner, _)) if equalToOperator(op) && either(inner) =>
      suggestFixForMustEither(elem, holder)
    case elem @ ScInfixExpr(_, op, inner: ScExpression) if equalToOperator(op) && optional(inner) =>
      suggestFixForMustOptional(elem, holder)
  }

  private def suggestFixForOptional(elem: PsiElement, holder: ProblemsHolder) =
    holder.registerProblem(elem, InspectionBundle.message("specs2.use.builtin.matcher"),
                           new ReplaceWithBeSomeOrNoneQuickFix(elem))

  private def suggestFixForMustOptional(elem: PsiElement, holder: ProblemsHolder) =
    holder.registerProblem(elem, InspectionBundle.message("specs2.use.builtin.matcher"),
                           new ReplaceMustWithBeSomeOrNoneQuickFix(elem))

  private def suggestFixForEither(elem: PsiElement, holder: ProblemsHolder) =
    holder.registerProblem(elem, InspectionBundle.message("specs2.use.builtin.matcher"),
                           new ReplaceWithBeLeftOrRightQuickFix(elem))

  private def suggestFixForMustEither(elem: PsiElement, holder: ProblemsHolder) =
    holder.registerProblem(elem, InspectionBundle.message("specs2.use.builtin.matcher"),
                           new ReplaceWithMustBeLeftOrRightQuickFix(elem))

  private val EqualToMatchers = Seq("be_===", "be_==", "beEqualTo", "equalTo", "beTypedEqualTo", "typedEqualTo")
  private val EqualToOperators = Seq("must_===", "must_==", "mustEqual")

  private def equalToMatcher(expr: ScExpression) = EqualToMatchers.contains(expr.getText)
  private def equalToOperator(expr: ScExpression) = EqualToOperators.contains(expr.getText)

  private def optional(expr: ScExpression) = expr.getText == "Some" || expr.getText == "None"
  private def either(expr: ScExpression) = expr.getText == "Left" || expr.getText == "Right"

  private class ReplaceWithBeSomeOrNoneQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(InspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    def doApplyFix(project: Project): Unit = {
      val expr = getElement
      expr match {
        case ScMethodCall(_, Seq(ScMethodCall(option , Seq(value)))) if option.getText == "Some" =>
          repair(s"beSome(${value.getText})", expr)
        case ScMethodCall(_, Seq(inner: ScExpression)) if inner.getText == "None" =>
          repair(s"beNone", expr)
      }
    }
  }

  private class ReplaceMustWithBeSomeOrNoneQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(InspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    def doApplyFix(project: Project): Unit = {
      val expr = getElement
      expr match {
        case ScInfixExpr(bexpr, op, ScMethodCall(option, Seq(value))) if option.getText == "Some" =>
          repair(s"${bexpr.getText} must beSome(${value.getText})", expr)
        case ScInfixExpr(bexpr, op, inner: ScExpression) if inner.getText == "None" =>
          repair(s"${bexpr.getText} must beNone", expr)
      }
    }
  }

  private class ReplaceWithBeLeftOrRightQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(InspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    def doApplyFix(project: Project): Unit =
      getElement match {
        case expr @ ScMethodCall(_, Seq(ScMethodCall(option , Seq(value)))) =>
          if (option.getText == "Left")
            repair(s"beLeft(${value.getText})", expr)
          if (option.getText == "Right")
            repair(s"beRight(${value.getText})", expr)
      }
  }

  private class ReplaceWithMustBeLeftOrRightQuickFix(element: PsiElement)
    extends AbstractFixOnPsiElement(InspectionBundle.message("specs2.builtin.matcher.alternative.exists"), element) {

    def doApplyFix(project: Project): Unit = {
      val expr = getElement
      expr match {
        case ScInfixExpr(bexpr, op, ScMethodCall(option, Seq(value))) if option.getText == "Left" =>
          repair(s"${bexpr.getText} must beLeft(${value.getText})", expr)
        case ScInfixExpr(bexpr, op, ScMethodCall(option, Seq(value))) if option.getText == "Right" =>
          repair(s"${bexpr.getText} must beRight(${value.getText})", expr)
      }
    }
  }

  private def repair(fixed: String, expr: PsiElement) = {
    val retStmt = createExpressionWithContextFromText(fixed, expr.getContext, expr)
    expr.replace(retStmt)
  }
}
