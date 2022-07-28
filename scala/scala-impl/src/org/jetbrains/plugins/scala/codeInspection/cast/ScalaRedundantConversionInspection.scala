package org.jetbrains.plugins.scala
package codeInspection
package cast

import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPostfixExpr, ScReferenceExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, TypePresentationContext}

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class ScalaRedundantConversionInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.redundant.conversion")) {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Unit] = {
    case element @ ScReferenceExpression.withQualifier(qualifier) && PsiReferenceEx.resolve(target) =>
      process(element, qualifier, target, qualifier.getTextLength, holder)
    case element @ ScPostfixExpr(operand, operator @ PsiReferenceEx.resolve(target)) =>
      process(element, operand, target, operator.getStartOffsetInParent, holder)
  }

  private def process(element: PsiElement, left: ScExpression, target: PsiElement, offset: Int, holder: ProblemsHolder): Unit = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(element)
    target match {
      case f: ScSyntheticFunction if f.name.startsWith("to") =>
        for {
          leftType <- left.`type`().toOption
          conversionType = f.retType if leftType.widen.equiv(conversionType)
        } registerProblem(element, left, conversionType.presentableText, offset, holder)
      case f: PsiMethod if f.name == "toString" &&
              f.getParameterList.getParametersCount == 0 &&
              (f.getTypeParameterList == null || f.getTypeParameterList.getTypeParameters.isEmpty) =>
        for {
          leftType <- left.`type`().toOption
          if conformsToTypeFromClass(leftType, "java.lang.String")(element)
        } registerProblem(element, left, "java.lang.String", offset, holder)
      case _ =>
    }
  }

  private def registerProblem(element: PsiElement, left: ScExpression, conversionType: String,
                      offset: Int, holder: ProblemsHolder): Unit = {
    val descriptor = {
      val range = new TextRange(offset, element.getTextLength)

      val message = ScalaInspectionBundle.message("casting.a.to.b.is.redundant", left.getText, conversionType)

      new ProblemDescriptorImpl(element, element, message, Array(new RemoveConversionQuickFix(element, left)),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, false, range, null, false)
    }

    holder.registerProblem(descriptor)
  }

  private class RemoveConversionQuickFix(element: PsiElement, expr: ScExpression)
          extends AbstractFixOnTwoPsiElements(ScalaInspectionBundle.message("remove.redundant.conversion"), element, expr) {

    override protected def doApplyFix(elem: PsiElement, scExpr: ScExpression)
                                     (implicit project: Project): Unit = {
      scExpr match {
        case under: ScUnderscoreSection if under.overExpr.contains(elem) =>
          elem.replace(ScalaPsiElementFactory.createIdentifier("identity").getPsi)
        case _ =>
          elem.replace(scExpr)
      }
    }
  }
}


