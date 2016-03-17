package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ExpressionType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScTypeExt}

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class MapGetOrElseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(MapGetOrElse)
}

object MapGetOrElse extends SimplificationType() {
  def hint = InspectionBundle.message("map.getOrElse.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    import expr.typeSystem
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

  def checkTypes(qual: ScExpression, mapArg: ScExpression, replacementText: String)
                (implicit typeSystem: TypeSystem): Boolean = {
    val mapArgRetType = mapArg match {
      case ExpressionType(ScFunctionType(retType, _)) => retType
      case _ => return false
    }
    ScalaPsiElementFactory.createExpressionFromText(replacementText, qual.getContext) match {
      case ScMethodCall(ScMethodCall(_, Seq(firstArg)), _) => mapArgRetType.conforms(firstArg.getType().getOrNothing)
      case _ => false
    }
  }

  def checkTypes(optionalBase: Option[ScExpression], mapArgs: Seq[ScExpression], getOrElseArgs: Seq[ScExpression])
                (implicit typeSystem: TypeSystem): Boolean = {
    val (mapArg, getOrElseArg) = (mapArgs, getOrElseArgs) match {
      case (Seq(a1), Seq(a2)) => (a1, a2)
      case _ => return false
    }
    val baseExpr = optionalBase match {
      case Some(e) => e
      case _ => return false
    }
    val mapArgRetType = mapArg.getType() match {
      case Success(ScFunctionType(retType, _), _) => retType
      case _ => return false
    }
    val firstArgText = stripped(getOrElseArg).getText
    val secondArgText = stripped(mapArg).getText
    val newExprText = s"${baseExpr.getText}.fold {$firstArgText}{$secondArgText}"
    ScalaPsiElementFactory.createExpressionFromText(newExprText, baseExpr.getContext) match {
      case ScMethodCall(ScMethodCall(_, Seq(firstArg)), _) => mapArgRetType.conforms(firstArg.getType().getOrNothing)
      case _ => false
    }
  }
}