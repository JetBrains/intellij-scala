package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr._

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
      case seqOfSeqs `.flatMap` (identityOperation()) =>
        Some(replace(expr).withText(invocationText(seqOfSeqs, "flatten")))
      case _ => None
    }

  private object identityOperation {
    def unapply(expr: ScExpression): Boolean = stripped(expr) match {
      case _ `.identity` (underscore()) => true
      case _ `.identity` () => true
      case undSect: ScUnderscoreSection =>
        undSect.bindingExpr match {
          case Some(_ `.identity` ()) => true
          case _ => false
        }
      case ScFunctionExpr(Seq(x), Some(ResolvesTo(param))) if x == param => true
      case ScFunctionExpr(Seq(x), Some(`.identity`(_, ResolvesTo(param)))) if x == param => true
      case _ => false
    }
  }

  private val `.identity` = invocation("identity").from(Array("scala.Predef"))

}