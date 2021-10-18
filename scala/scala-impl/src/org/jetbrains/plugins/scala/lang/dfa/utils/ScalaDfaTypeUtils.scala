package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import com.intellij.codeInspection.dataFlow.{Mutability, TypeConstraints}
import com.intellij.psi.{PsiClass, PsiElement, PsiMember, PsiNamedElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ExpressionTransformer
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Any

//noinspection UnstableApiUsage
object ScalaDfaTypeUtils {

  def dfTypeImmutableCollection(size: Int): DfType = {
    SpecialField.COLLECTION_SIZE.asDfType(DfTypes.intValue(size))
      .meet(Mutability.UNMODIFIABLE.asDfType())
  }

  def literalToDfType(literal: ScLiteral): DfType = literal match {
    case _: ScNullLiteral => DfTypes.NULL
    case int: ScIntegerLiteral => DfTypes.intValue(int.getValue)
    case long: ScLongLiteral => DfTypes.longValue(long.getValue)
    case float: ScFloatLiteral => DfTypes.floatValue(float.getValue)
    case double: ScDoubleLiteral => DfTypes.doubleValue(double.getValue)
    case boolean: ScBooleanLiteral => DfTypes.booleanValue(boolean.getValue)
    case char: ScCharLiteral => DfTypes.intValue(char.getValue.toInt)
    case _ => DfType.TOP
  }

  def dfTypeToReportedConstant(dfType: DfType): DfaConstantValue = dfType match {
    case DfTypes.TRUE => DfaConstantValue.True
    case DfTypes.FALSE => DfaConstantValue.False
    case _ => DfaConstantValue.Unknown
  }

  @Nls
  def constantValueToProblemMessage(value: DfaConstantValue, warningType: ProblemHighlightType): String = value match {
    case DfaConstantValue.True => ScalaInspectionBundle.message("condition.always.true", warningType)
    case DfaConstantValue.False => ScalaInspectionBundle.message("condition.always.false", warningType)
    case _ => throw new IllegalStateException(s"Trying to report an unexpected DFA constant: $value")
  }

  @Nls
  def exceptionNameToProblemMessage(exceptionName: String): String = {
    val warningType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    exceptionName match {
      case IndexOutOfBoundsExceptionName =>
        ScalaInspectionBundle.message("invocation.index.out.of.bounds", warningType)
      case NoSuchElementExceptionName =>
        ScalaInspectionBundle.message("invocation.no.such.element", warningType)
      case NullPointerExceptionName =>
        ScalaInspectionBundle.message("invocation.null.pointer", warningType)
      case _ => throw new IllegalStateException(s"Trying to report an unexpected DFA exception: $exceptionName")
    }
  }

  //noinspection UnstableApiUsage
  def scTypeToDfType(scType: ScType): DfType = scType.extractClass match {
    case Some(psiClass) if psiClass.qualifiedName.startsWith(s"$ScalaCollectionImmutable.Nil") =>
      dfTypeImmutableCollection(0)
    case Some(psiClass) if scType == Any(psiClass.getProject) => DfType.TOP
    case Some(psiClass) => psiClass.qualifiedName match {
      case "scala.Int" => DfTypes.INT
      case "scala.Long" => DfTypes.LONG
      case "scala.Float" => DfTypes.FLOAT
      case "scala.Double" => DfTypes.DOUBLE
      case "scala.Boolean" => DfTypes.BOOLEAN
      case "scala.Char" => DfTypes.intRange(LongRangeSet.range(Char.MinValue.toLong, Character.MAX_VALUE.toLong))
      case "scala.Short" => DfTypes.intRange(LongRangeSet.range(Short.MinValue.toLong, Short.MaxValue.toLong))
      case "scala.Byte" => DfTypes.intRange(LongRangeSet.range(Byte.MinValue.toLong, Byte.MaxValue.toLong))
      case _ => TypeConstraints.exactClass(psiClass).asDfType().meet(DfTypes.OBJECT_OR_NULL)
    }
    case _ => DfType.TOP
  }

  def isStableElement(element: PsiNamedElement): Boolean = element match {
    case valueOrVariable: ScValueOrVariable => valueOrVariable.isStable
    case typedDefinition: ScTypedDefinition => typedDefinition.isStable
    case _ => false
  }

  def extractExpressionFromArgument(argument: Argument): Option[ScExpression] = argument.content match {
    case expressionTransformer: ExpressionTransformer => Some(expressionTransformer.wrappedExpression)
    case _ => None
  }

  def scalaClass(psiClass: PsiClass): Option[ScClass] = psiClass match {
    case scalaClass: ScClass => Some(scalaClass)
    case _ => None
  }

  def isPsiClassCase(psiClass: PsiClass): Boolean = psiClass match {
    case typeDefinition: ScTypeDefinition => typeDefinition.isCase
    case _ => false
  }

  def containingScalaObject(element: PsiElement): Option[ScObject] = element match {
    case member: PsiMember => member.containingClass match {
      case scalaObject: ScObject => Some(scalaObject)
      case _ => None
    }
  }
}
