package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.value.RelationType

object ScalaDfaConstants {

  sealed trait DfaConstantValue
  final object DfaConstantValue {
    case object True extends DfaConstantValue
    case object False extends DfaConstantValue
    case object Zero extends DfaConstantValue
    case object Null extends DfaConstantValue
    case object Other extends DfaConstantValue
  }

  sealed trait LogicalOperation
  final object LogicalOperation {
    case object And extends LogicalOperation
    case object Or extends LogicalOperation
    case object Not extends LogicalOperation
  }

  final object Exceptions {
    val ScalaMatchError = "scala.MatchError"
  }

  final object Packages {
    val ScalaUnit = "Scala.Unit"
    val ScalaBoolean = "scala.Boolean"
    val ScalaByte = "scala.Byte"
    val ScalaChar = "scala.Char"
    val ScalaShort = "scala.Short"
    val ScalaInt = "scala.Int"
    val ScalaLong = "scala.Long"
    val ScalaFloat = "scala.Float"
    val ScalaDouble = "scala.Double"
    val ScalaNone = "scala.None"
    val ScalaNothing = "scala.Nothing"
    val ScalaCollection = "scala.collection"
    val ScalaCollectionImmutable = s"$ScalaCollection.immutable"
    val ScalaCollectionMutable = s"$ScalaCollection.mutable"
    val ScalaMath = "scala.math"
    val JavaLangMath = "java.lang.Math"
    val IndexOutOfBoundsExceptionName = "java.lang.IndexOutOfBoundsException"
    val NoSuchElementExceptionName = "java.util.NoSuchElementException"
    val NullPointerExceptionName = "java.util.NullPointerException"
    val Apply = "apply"
  }

  val NumericPrimitives: Seq[String] = Seq(
    Packages.ScalaByte,
    Packages.ScalaChar,
    Packages.ScalaShort,
    Packages.ScalaInt,
    Packages.ScalaLong,
    Packages.ScalaFloat,
    Packages.ScalaDouble,
  )

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
