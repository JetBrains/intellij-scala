package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.value.RelationType

object ScalaDfaTypeConstants {

  sealed trait DfaConstantValue
  final object DfaConstantValue {
    case object True extends DfaConstantValue
    case object False extends DfaConstantValue
    case object Unknown extends DfaConstantValue
  }

  sealed trait LogicalOperation
  final object LogicalOperation {
    case object And extends LogicalOperation
    case object Or extends LogicalOperation
  }

  val ScalaCollectionImmutable = "scala.collection.immutable"

  val BooleanTypeClass: String = "scala.Boolean"

  val NumericTypeClasses: Seq[String] =
    for (typeName <- List("Int", "Long", "Float", "Double"))
      yield "scala." + typeName

  val NumericOperations: Map[String, LongRangeBinOp] = Map(
    "+" -> LongRangeBinOp.PLUS,
    "-" -> LongRangeBinOp.MINUS,
    "*" -> LongRangeBinOp.MUL,
    "/" -> LongRangeBinOp.DIV,
    "%" -> LongRangeBinOp.MOD
  )

  val RelationalOperations: Map[String, RelationType] = Map(
    "<" -> RelationType.LT,
    "<=" -> RelationType.LE,
    ">" -> RelationType.GT,
    ">=" -> RelationType.GE,
    "==" -> RelationType.EQ,
    "!=" -> RelationType.NE
  )

  val LogicalOperations: Map[String, LogicalOperation] = Map(
    "&&" -> LogicalOperation.And,
    "||" -> LogicalOperation.Or
  )
}
