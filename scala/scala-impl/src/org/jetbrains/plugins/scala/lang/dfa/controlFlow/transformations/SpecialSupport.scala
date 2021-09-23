package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.plugins.scala.lang.dfa.ScalaDfaTypeUtils.LogicalOperation

object SpecialSupport {

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
