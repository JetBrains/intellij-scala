package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

final class SubstringZeroInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Seq[SimplificationType] = {
    ArraySeq(DropSubstringZeroOption)
  }
}

object DropSubstringZeroOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("substring.with.0.is.redundant")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.substring`arg if arg.textMatches("0") =>
        Some(replace(expr).withText(qual.getText).highlightFrom(qual))
      case _ => None
    }
  }
}