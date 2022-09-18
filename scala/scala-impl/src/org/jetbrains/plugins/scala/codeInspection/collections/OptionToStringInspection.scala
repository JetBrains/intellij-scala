package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

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
        case Right(t: ScParameterizedType) if t.typeArguments.forall(SizeToLength.isString) =>
          Some(replace(expr).withText(invocationText(opt, "getOrElse(throw new NoSuchElementException())")).highlightFrom(expr))
        case Right(t: ScDesignatorType) if (t.extractDesignatorSingleton match {
          case Some(t: ScParameterizedType) if t.typeArguments.forall(SizeToLength.isString) => true
          case _ => false
        }) => Some(replace(expr).withText(invocationText(opt, "getOrElse(throw new NoSuchElementException())")).highlightFrom(expr))
        case _ =>
          Some(replace(expr).withText(invocationText(opt, "map(_.toString).getOrElse(throw new NoSuchElementException())")).highlightFrom(expr))
      }
    case _ => None
  }
}