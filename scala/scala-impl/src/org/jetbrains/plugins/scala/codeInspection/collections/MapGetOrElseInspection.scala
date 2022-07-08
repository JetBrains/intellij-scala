package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.collection.immutable.ArraySeq

class MapGetOrElseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(MapGetOrElse)
}

object MapGetOrElse extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("map.getOrElse.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.mapOnOption` fun `.getOrElse` default =>
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
      case ScMethodCall(ScMethodCall(_, Seq(firstArg)), _) =>
        mapArgRetType.conforms(firstArg.`type`().getOrNothing.widenIfLiteral)
      case _ => false
    }
  }

}