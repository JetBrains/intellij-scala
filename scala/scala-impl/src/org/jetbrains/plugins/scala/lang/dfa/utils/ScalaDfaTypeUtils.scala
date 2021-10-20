package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types._
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.{Mutability, TypeConstraints}
import com.intellij.psi.PsiNamedElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.ProperArgument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ExpressionTransformer
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Any

//noinspection UnstableApiUsage
object ScalaDfaTypeUtils {

  def dfTypeImmutableCollectionFromSizeDfType(sizeDfType: DfType): DfType = {
    SpecialField.COLLECTION_SIZE.asDfType(sizeDfType)
      .meet(Mutability.UNMODIFIABLE.asDfType())
  }

  def dfTypeImmutableCollectionFromSize(size: Int): DfType = {
    dfTypeImmutableCollectionFromSizeDfType(DfTypes.intValue(size))
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
    case _ => Option(dfType.getConstantOfType(classOf[Number]))
      .filter(value => value.intValue() == 0 || value.longValue() == 0L)
      .map(_ => DfaConstantValue.Zero)
      .getOrElse(DfaConstantValue.Unknown)
  }

  @Nls
  def constantValueToProblemMessage(value: DfaConstantValue, warningType: ProblemHighlightType): String = value match {
    case DfaConstantValue.True => ScalaInspectionBundle.message("condition.always.true", warningType)
    case DfaConstantValue.False => ScalaInspectionBundle.message("condition.always.false", warningType)
    case DfaConstantValue.Zero => ScalaInspectionBundle.message("expression.always.zero", warningType)
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
      dfTypeImmutableCollectionFromSize(0)
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

  def findArgumentsPrimitiveType(argumentValues: Map[Argument, DfaValue]): Option[String] = {
    argumentValues.filter(_._1.kind.is[ProperArgument]).values.headOption.map(_.getDfType) match {
      case Some(_: DfIntType) => Some(ScalaInt)
      case Some(_: DfLongType) => Some(ScalaLong)
      case Some(_: DfDoubleType) => Some(ScalaDouble)
      case Some(_: DfFloatType) => Some(ScalaFloat)
      case _ => None
    }
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
}
