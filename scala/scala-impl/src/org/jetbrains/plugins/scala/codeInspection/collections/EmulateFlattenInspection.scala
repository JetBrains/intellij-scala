package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType

/**
  * @author Lukasz Piepiora
  */
class EmulateFlattenInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FlattenSimplification)
}

object FlattenSimplification extends SimplificationType {

  override def hint: String = InspectionBundle.message("replace.with.flatten")

  override def getSimplification(expr: ScExpression): Option[Simplification] =
    expr match {
      case seqOfSeqs `.flatMap` (identityOperation()) => toSimplification(expr, seqOfSeqs)
      case qual `.getOrElse` (scalaNone()) if isNestedOption(qual) => toSimplification(expr, qual)
      case qual `.map` (underscore() `.get` ()) if isNestedOption(qual) => toSimplification(expr, qual)
      case _ => None
    }

  private def toSimplification(expr: ScExpression, qual: ScExpression): Some[Simplification] =
    Some(replace(expr).withText(invocationText(qual, "flatten")))

  private object identityOperation {
    def unapply(expr: ScExpression): Boolean = stripped(expr) match {
      case identity(underscore()) => true
      case identity() => true
      case undSect: ScUnderscoreSection =>
        undSect.bindingExpr match {
          case Some(identity()) => true
          case _ => false
        }
      case ScFunctionExpr(Seq(x), Some(ResolvesTo(param))) if x == param => true
      case ScFunctionExpr(Seq(x), Some(identity(ResolvesTo(param)))) if x == param => true
      case _ => false
    }
  }

  object identity {
    private val qualIdentity = invocation("identity").from(Array("scala.Predef"))
    private val unqualIdentity = unqualifed("identity").from(Array("scala.Predef"))

    def unapplySeq(expr: ScExpression): Option[Seq[ScExpression]] = expr match {
      case _ qualIdentity(arg) => Some(Seq(arg))
      case _ qualIdentity() => Some(Nil)
      case unqualIdentity(arg) => Some(Seq(arg))
      case unqualIdentity() => Some(Nil)
      case _ => None
    }
  }

  private def isNestedOption(qual: ScExpression) = qual.`type`().toOption.map(_.tryExtractDesignatorSingleton) match {
    case Some(outer: ScParameterizedType) if isOption(outer.designator) => outer.typeArguments match {
      case Seq(inner: ScParameterizedType) => isOption(inner.designator)
      case _ => false
    }
    case _ => false
  }

}