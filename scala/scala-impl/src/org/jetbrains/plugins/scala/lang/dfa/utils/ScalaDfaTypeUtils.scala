package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types._
import com.intellij.codeInspection.dataFlow.value.{DfaValue, DfaValueFactory}
import com.intellij.codeInspection.dataFlow.{DfaPsiUtil, Mutability}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.ProperArgument
import org.jetbrains.plugins.scala.lang.dfa.types.DfUnitType
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages._
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValueOrVariable, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

//noinspection UnstableApiUsage
object ScalaDfaTypeUtils {

  def unknownDfaValue(implicit factory: DfaValueFactory): DfaValue = factory.fromDfType(DfType.TOP)

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
    case string: ScStringLiteral =>
      string.getNonValueType() match {
        case Right(ty) => DfTypes.constant(string.getValue, scTypeToDfType(ty))
        case Left(_) => DfType.TOP
      }
    case _ => DfType.TOP
  }

  def dfTypeToReportedConstant(dfType: DfType): DfaConstantValue = dfType match {
    case DfTypes.TRUE => DfaConstantValue.True
    case DfTypes.FALSE => DfaConstantValue.False
    case DfTypes.NULL => DfaConstantValue.Other
    // Reporting "always null" is currently disabled, because it interacted very badly with some other parts
    // of the analysis, producing many false "null" warnings. This might be, at least partially, a problem in
    // the Java side of the analysis. It should be revived with implementation of more complete analysis of
    // nullability, more useful than just "always null" warnings.
    case integralType: DfIntegralType => Option(integralType.getConstantOfType(classOf[Number]))
      .filter(value => value.intValue() == 0 || value.longValue() == 0L)
      .map(_ => DfaConstantValue.Zero)
      .getOrElse(DfaConstantValue.Other)
    case _ => DfaConstantValue.Other
  }

  @Nls
  def constantValueToProblemMessage(value: DfaConstantValue, warningType: ProblemHighlightType): String = value match {
    case DfaConstantValue.True => ScalaInspectionBundle.message("condition.always.true", warningType)
    case DfaConstantValue.False => ScalaInspectionBundle.message("condition.always.false", warningType)
    case DfaConstantValue.Zero => ScalaInspectionBundle.message("expression.always.zero", warningType)
    case DfaConstantValue.Null => ScalaInspectionBundle.message("expression.always.null", warningType)
    case _ => throw new IllegalStateException(s"Trying to report an unexpected DFA constant: $value")
  }

  //noinspection UnstableApiUsage
  def scTypeToDfType(scType: ScType, nullability: Nullability = Nullability.UNKNOWN): DfType = {
    val extractedClass = scType match {
      case literalType: ScLiteralType => literalType.wideType.extractClass
      case _ => scType.extractClass
    }

    extractedClass match {
      case Some(psiClass) if psiClass.qualifiedNameOpt.exists(_.startsWith(s"$ScalaCollectionImmutable.Nil")) =>
        dfTypeImmutableCollectionFromSize(0)
      case Some(psiClass) if psiClass.qualifiedName == ScalaNone || psiClass.qualifiedName == ScalaNothing => DfType.TOP
      case Some(psiClass) if scType == Any(psiClass.getProject) => DfType.TOP
      case Some(psiClass) =>
        psiClass.qualifiedName match {
          case "scala.Unit" => DfUnitType
          case "scala.Int" => DfTypes.INT
          case "scala.Long" => DfTypes.LONG
          case "scala.Float" => DfTypes.FLOAT
          case "scala.Double" => DfTypes.DOUBLE
          case "scala.Boolean" => DfTypes.BOOLEAN
          case "scala.Char" => DfTypes.intRange(LongRangeSet.range(Char.MinValue.toLong, Character.MAX_VALUE.toLong))
          case "scala.Short" => DfTypes.intRange(LongRangeSet.range(Short.MinValue.toLong, Short.MaxValue.toLong))
          case "scala.Byte" => DfTypes.intRange(LongRangeSet.range(Byte.MinValue.toLong, Byte.MaxValue.toLong))
          //case _ => TypeConstraints.exactClass(psiClass).instanceOf().asDfType()
          case _ => DfTypes.typedObject(scType.toPsiType, nullability)
        }
      case _ => DfType.TOP
    }
  }

  def isStableElement(element: PsiElement): Boolean = element match {
    case referencePattern: ScReferencePattern => referencePattern.isStable && !referencePattern.isVar
    case _: ScVariable => false
    case valueOrVariable: ScValueOrVariable => valueOrVariable.isStable
    case typedDefinition: ScTypedDefinition => typedDefinition.isStable && !typedDefinition.isVar
    case _ => false
  }

  def resolveExpressionType(expression: ScExpression): ScType = {
    implicit val context: ProjectContext = expression.getProject
    expression match {
      // A fix for very weird behaviour of literals in some specific cases
      case _: ScIntegerLiteral => code"0".asInstanceOf[ScExpression].`type`().getOrAny
      case _: ScLongLiteral => code"0L".asInstanceOf[ScExpression].`type`().getOrAny
      case _: ScDoubleLiteral => code"0.0".asInstanceOf[ScExpression].`type`().getOrAny
      case _: ScFloatLiteral => code"0.0F".asInstanceOf[ScExpression].`type`().getOrAny
      case reference: ScReferenceExpression => reference.bind().map(_.element) match {
        case Some(resolved: Typeable) => resolved.`type`().getOrAny
        case _ => expression.`type`().getOrAny
      }
      case _ => expression.`type`().getOrAny
    }
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

  def balanceType(leftType: Option[ScType], rightType: Option[ScType],
                  forceEqualityByContent: Boolean): Option[ScType] = {
    (leftType, rightType) match {
      case (Some(leftType), Some(rightType)) if forceEqualityByContent &&
        !isPrimitiveType(leftType) && !isPrimitiveType(rightType) => balanceTypeForEqualityByContent(leftType, rightType)
      case (Some(leftType), Some(rightType)) => lookForStandardBalancing(leftType, rightType)
      case _ => None
    }
  }

  def isPrimitiveType(scType: ScType): Boolean = scTypeToDfType(scType).is[DfPrimitiveType]

  private def balanceTypeForEqualityByContent(leftType: ScType, rightType: ScType): Option[ScType] = {
    if (leftType conforms rightType) Some(rightType)
    else if (rightType conforms leftType) Some(leftType)
    else None
  }

  private def lookForStandardBalancing(leftType: ScType, rightType: ScType): Option[ScType] = {
    val leftDfType = scTypeToDfType(leftType)
    val rightDfType = scTypeToDfType(rightType)

    if (leftType == rightType) None
    else (leftDfType, rightDfType) match {
      case (DfTypes.DOUBLE, _) => Some(leftType)
      case (_, DfTypes.DOUBLE) => Some(rightType)
      case (DfTypes.FLOAT, _) => Some(leftType)
      case (_, DfTypes.FLOAT) => Some(rightType)
      case (DfTypes.LONG, _) => Some(leftType)
      case (_, DfTypes.LONG) => Some(rightType)
      case _ => None
    }
  }

  def nullability(param: Parameter): Nullability =
    DfaPsiUtil.getElementNullability(param.paramType.toPsiType, param.psiParam.orNull)
}
