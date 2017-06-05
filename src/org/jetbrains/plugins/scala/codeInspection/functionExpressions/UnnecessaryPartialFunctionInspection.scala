package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiClass, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.functionExpressions.UnnecessaryPartialFunctionInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, UndefinedType, ValueType}
import org.jetbrains.plugins.scala.project.ProjectContext

object UnnecessaryPartialFunctionInspection {
  private val inspectionId = "UnnecessaryPartialFunction"
  private val PartialFunctionClassName = classOf[PartialFunction[_, _]].getCanonicalName
  private val Function1ClassName = classOf[(_) => _].getCanonicalName
  val inspectionName = InspectionBundle.message("unnecessary.partial.function")
}

class UnnecessaryPartialFunctionInspection
  extends AbstractInspection(inspectionId, inspectionName){

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expression: ScBlockExpr =>
      def isNotPartialFunction(expectedType: ScType) =
        findPartialFunctionType(holder.getFile).exists(!expectedType.conforms(_))
      def conformsTo(expectedType: ScType) = (inputType: ScType, resultType: ScType) =>
        findType(holder.getFile, Function1ClassName, _ => Seq(inputType, resultType)).exists(_.conforms(expectedType))

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
          new UnnecessaryPartialFunctionQuickFix(expression))
  }

  private def findType(file: PsiFile, className: String, parameterTypes: PsiClass => Seq[ScType]): Option[ValueType] ={
      implicit val ctx: ProjectContext = file
      ScalaPsiManager.instance
        .getCachedClass(file.getResolveScope, className)
        .map(clazz =>
          ScParameterizedType(ScDesignatorType(clazz), parameterTypes(clazz)))
    }


  private def findPartialFunctionType(file: PsiFile): Option[ValueType] =
    findType(file, PartialFunctionClassName, undefinedTypeParameters)

  private def undefinedTypeParameters(clazz: PsiClass): Seq[UndefinedType] = {
    implicit val ctx: ProjectContext = clazz

    clazz
      .getTypeParameters
      .map(typeParameter => UndefinedType(TypeParameterType(typeParameter)))
      .toSeq
  }

  private def canBeConvertedToFunction(caseClause: ScCaseClause, conformsToExpectedType: (ScType, ScType) => Boolean) =
    caseClause.guard.isEmpty &&
      caseClause.pattern.exists {
        case _: ScReferencePattern => true
        case _: ScWildcardPattern => true
        case typedPattern: ScTypedPattern =>
          val patternType = typedPattern.typePattern.map(_.typeElement.calcType)
          val expressionType = caseClause.expr.flatMap(_.getType().toOption)
          (patternType, expressionType) match {
            case (Some(inputType), Some(returnType)) => conformsToExpectedType(inputType, returnType)
            case _ => false
          }
        case _ => false
      }
}
