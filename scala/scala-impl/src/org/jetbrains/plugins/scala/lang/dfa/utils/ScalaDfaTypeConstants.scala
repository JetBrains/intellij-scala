package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.value.RelationType

object ScalaDfaTypeConstants {

  sealed trait DfaConstantValue
  final object DfaConstantValue {
    case object True extends DfaConstantValue
    case object False extends DfaConstantValue
    case object Zero extends DfaConstantValue
    case object Unknown extends DfaConstantValue
  }

  sealed trait LogicalOperation
  final object LogicalOperation {
    case object And extends LogicalOperation
    case object Or extends LogicalOperation
    case object Not extends LogicalOperation
  }

  final object Packages {

    val ScalaNumericTypes: Seq[String] =
      for (typeName <- List("Int", "Long", "Float", "Double"))
        yield "scala." + typeName

    val ScalaBoolean = "scala.Boolean"

    val ScalaNil = "scala.Nil"

    val ScalaCollection = "scala.collection"

    val ScalaCollectionImmutable = s"$ScalaCollection.immutable"

    val IndexOutOfBoundsExceptionName = "java.lang.IndexOutOfBoundsException"

    val NoSuchElementExceptionName = "java.util.NoSuchElementException"

    val NullPointerExceptionName = "java.util.NullPointerException"

    val Apply = "apply"
  }

  final object SyntheticOperators {

    val NumericBinary: Map[String, LongRangeBinOp] = Map(
      "+" -> LongRangeBinOp.PLUS,
      "-" -> LongRangeBinOp.MINUS,
      "*" -> LongRangeBinOp.MUL,
      "/" -> LongRangeBinOp.DIV,
      "%" -> LongRangeBinOp.MOD
    )

    val RelationalBinary: Map[String, RelationType] = Map(
      "<" -> RelationType.LT,
      "<=" -> RelationType.LE,
      ">" -> RelationType.GT,
      ">=" -> RelationType.GE,
      "==" -> RelationType.EQ,
      "!=" -> RelationType.NE
    )

    val LogicalBinary: Map[String, LogicalOperation] = Map(
      "&&" -> LogicalOperation.And,
      "||" -> LogicalOperation.Or
    )

    val NumericUnary: Map[String, LongRangeBinOp] = Map(
      "unary_+" -> LongRangeBinOp.PLUS,
      "unary_-" -> LongRangeBinOp.MINUS
    )

    val LogicalUnary: Map[String, LogicalOperation] = Map(
      "unary_!" -> LogicalOperation.Not
    )
  }
}
