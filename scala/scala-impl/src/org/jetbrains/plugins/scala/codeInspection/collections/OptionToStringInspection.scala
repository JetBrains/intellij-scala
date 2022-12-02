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

  override def hint: String = ScalaInspectionBundle.message("option.toString.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] =
    getToStringSimplification(expr, isOption, mkString, replace)

  private def mkString(option: ScExpression): String = {
    option match {
      case scalaNone() => """getOrElse("null")"""
      case _ =>
        option.getTypeAfterImplicitConversion().tr match {
          case Right(t: ScParameterizedType) if isStringInOption(t) => onString
          case Right(t: ScDesignatorType) if isStringInOption(t) => onString
          case Right(t: ScProjectionType) if isStringInOption(t) => onString
          case _ => onNotString
        }
    }
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

  private def onString: String = """getOrElse("")"""

  private def onNotString: String = """map(_.toString).getOrElse("")"""
}
