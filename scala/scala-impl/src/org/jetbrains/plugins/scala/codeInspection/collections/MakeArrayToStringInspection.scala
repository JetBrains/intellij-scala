package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MakeArrayToStringInspection  extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(MakeArrayToStringInspection)
}

object MakeArrayToStringInspection extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("format.with.mkstring")

  private val `print` = unqualifed(Set("print", "println")).from(ArraySeq("scala.Predef", "java.io.PrintStream"))
  private val `.print` = invocation(Set("print", "println")).from(ArraySeq("scala.Predef", "java.io.PrintStream"))
  private val `+` = invocation(Set("+"))

  private val mkString = """mkString("Array(", ", ", ")")"""

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.toString`(array) if isArray(array) =>
        // array.toString
        Some(replace(expr).withText(invocationText(array, mkString)).highlightFrom(array))
      case someString `+` array if isString(someString) && isArray(array) =>
        // "string" + array
        Some(replace(array).withText(invocationText(array, mkString)).highlightFrom(array))
      case array `+` someString if isString(someString) && isArray(array) =>
        // array + "string"
        Some(replace(array).withText(invocationText(array, mkString)).highlightFrom(array))
      case _ if isArray(expr) =>
        def result: SimplificationBuilder = replace(expr).withText(invocationText(expr, mkString)).highlightFrom(expr)

        expr.getParent match {
          case lit: ScInterpolatedStringLiteral if isInBuiltinStringInterpolator(lit) =>
            // s"start $array end"
            Some(result.wrapInBlock())
          case null => None
          case parent =>
            parent.getParent match {
              case `.print`(_, args@_*) if args.contains(expr) =>
                // System.out.println(array)
                Some(result)
              case `print` (args@_*) if args.contains(expr) =>
                // println(array)
                Some(result)
              case lit: ScInterpolatedStringLiteral if isInBuiltinStringInterpolator(lit) =>
                // s"start ${array} end"
                Some(result)
              case _ => None
            }
        }
      case _ =>
        None
    }
  }

  private val builtinStringInterpolators = Set("s", "f", "raw")

  private def isInBuiltinStringInterpolator(lit: ScInterpolatedStringLiteral): Boolean =
    builtinStringInterpolators.contains(lit.referenceName)
}
