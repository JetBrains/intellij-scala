package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}

import scala.collection.immutable.ArraySeq

class OptionToStringInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(OptionToStringInspection)
}

object OptionToStringInspection extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("option.getOrElse.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case `.toString`(scalaNone()) => None
    case `.toString`(opt) if isOption(opt) =>
      opt.getTypeAfterImplicitConversion().tr match {
        case Right(t: ScParameterizedType) if isStringInOption(t) => onString(opt, expr)
        case Right(t: ScDesignatorType) if isStringInOption(t) => onString(opt, expr)
        case Right(t: ScProjectionType) if isStringInOption(t) => onString(opt, expr)
        case _ => onNotString(opt, expr)
      }
    case _ => None
  }

  private def isStringInOption(t: ScParameterizedType): Boolean = t.typeArguments.forall(SizeToLength.isString)

  private def isStringInOption(t: ScDesignatorType): Boolean = {
    t.extractDesignatorSingleton match {
      case Some(t: ScParameterizedType) if isStringInOption(t) => true
      case _ => false
    }
  }

  private def isStringInOption(t: ScProjectionType): Boolean = {
    t.extractDesignatorSingleton match {
      case Some(t: ScParameterizedType) if isStringInOption(t) => true
      case _ => false
    }
  }

  private def onString(opt: ScExpression, expr: ScExpression): Option[Simplification] =
    Some(replace(expr).withText(invocationText(opt, "getOrElse(throw new NoSuchElementException())")).highlightFrom(opt))

  private def onNotString(opt: ScExpression, expr: ScExpression): Option[Simplification] =
    Some(replace(expr).withText(invocationText(opt, "map(_.toString).getOrElse(throw new NoSuchElementException())")).highlightFrom(opt))
}