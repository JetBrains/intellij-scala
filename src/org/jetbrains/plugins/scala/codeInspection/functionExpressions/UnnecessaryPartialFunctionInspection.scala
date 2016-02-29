package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.codeInspection.functionExpressions.UnnecessaryPartialFunctionInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeParameterType, _}

object UnnecessaryPartialFunctionInspection {
  private val inspectionId = "UnnecessaryPartialFunction"
  private val PartialFunctionClassName = classOf[PartialFunction[_, _]].getCanonicalName
  private val Function1ClassName = classOf[(_) => _].getCanonicalName
  val inspectionName = InspectionBundle.message("unnecessary.partial.function")
}

class UnnecessaryPartialFunctionInspection
  extends AbstractInspection(inspectionId, inspectionName){

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expression: ScBlockExpr =>
      def isNotPartialFunction(expectedType: ScType) =
        findPartialFunctionType(holder).exists(!expectedType.conforms(_))
      def conformsTo(expectedType: ScType) = (inputType: ScType, resultType: ScType) =>
        findType(holder, Function1ClassName, _ => Seq(inputType, resultType)).exists(_.conforms(expectedType))

      for{
        expectedExpressionType <- expression.expectedType()
        if isNotPartialFunction(expectedExpressionType)
        Seq(singleCaseClause) <- expression.caseClauses.map(_.caseClauses)
        if canBeConvertedToFunction(singleCaseClause, conformsTo(expectedExpressionType))
        caseKeyword <- singleCaseClause.firstChild
      } holder.registerProblem(
          caseKeyword,
          inspectionName,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          new UnnecessaryPartialFunctionQuickFix(singleCaseClause, expression))
  }

  private def findType(holder: ProblemsHolder, className: String, parameterTypes: PsiClass => Seq[ScType]): Option[ValueType] =
    ScalaPsiManager
      .instance(holder.getProject)
      .getCachedClass(holder.getFile.getResolveScope, className)
        .map(clazz =>
          ScParameterizedType(ScDesignatorType(clazz), parameterTypes(clazz)))


  private def findPartialFunctionType(holder: ProblemsHolder): Option[ValueType] =
    findType(holder, PartialFunctionClassName, undefinedTypeParameters)

  private def undefinedTypeParameters(clazz: PsiClass): Seq[ScUndefinedType] =
    clazz
      .getTypeParameters
      .map(typeParameter =>
        new ScUndefinedType(new ScTypeParameterType(typeParameter, ScSubstitutor.empty)))
      .toSeq

  private def canBeConvertedToFunction(caseClause: ScCaseClause, conformsToExpectedType: (ScType, ScType) => Boolean) =
    caseClause.guard.isEmpty &&
      caseClause.pattern.exists {
        case reference: ScReferencePattern => true
        case wildcard: ScWildcardPattern => true
        case typed: ScTypedPattern =>
          typed.typePattern.map(_.typeElement.calcType).exists(inputType =>
            caseClause.expr.flatMap(_.expectedType()).exists(returnType =>
                conformsToExpectedType(inputType, returnType)))
        case _ => false
      }
}
