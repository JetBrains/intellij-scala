package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class MapGetOrElseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(MapGetOrElse)
}

object MapGetOrElse extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("map.getOrElse.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.mapOnOption`(fun)`.getOrElse`(default) =>
        replacementText(qual, fun, default) match {
          case Some(newText) if checkTypes(qual, fun, newText) =>
            val simplification = replace(expr).withText(newText).highlightFrom(qual)
            Some(simplification)
          case _ => None
        }
      case _ => None
    }
  }

  def replacementText(qual: ScExpression, mapArg: ScExpression, goeArg: ScExpression): Option[String] = {
    val firstArgText = argListText(Seq(goeArg))
    val secondArgText = argListText(Seq(mapArg))
    Some(s"${qual.getText}.fold $firstArgText$secondArgText")
  }

  def checkTypes(qual: ScExpression, mapArg: ScExpression, replacementText: String): Boolean = {

    val mapArgRetType = mapArg match {
      case Typeable(FunctionType(retType, _)) => retType
      case _ => return false
    }
    ScalaPsiElementFactory.createExpressionFromText(replacementText, qual.getContext) match {
      case ScMethodCall(ScMethodCall(_, Seq(firstArg)), _) => mapArgRetType.conforms(firstArg.`type`().getOrNothing)
      case _ => false
    }
  }

  def checkTypes(optionalBase: Option[ScExpression], mapArgs: Seq[ScExpression], getOrElseArgs: Seq[ScExpression]): Boolean = {
    val (mapArg, getOrElseArg) = (mapArgs, getOrElseArgs) match {
      case (Seq(a1), Seq(a2)) => (a1, a2)
      case _ => return false
    }
    val baseExpr = optionalBase match {
      case Some(e) => e
      case _ => return false
    }
    val mapArgRetType = mapArg.`type`() match {
      case Right(FunctionType(retType, _)) => retType
      case _ => return false
    }

    val firstArgText = stripped(getOrElseArg).getText
    val secondArgText = stripped(mapArg).getText
    val newExprText = s"${baseExpr.getText}.fold {$firstArgText}{$secondArgText}"
    ScalaPsiElementFactory.createExpressionFromText(newExprText, baseExpr.getContext) match {
      case ScMethodCall(ScMethodCall(_, Seq(firstArg)), _) => mapArgRetType.conforms(firstArg.`type`().getOrNothing)
      case _ => false
    }
  }
}