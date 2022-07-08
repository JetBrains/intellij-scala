package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

import scala.collection.immutable.ArraySeq

class OptionEqualsSomeToContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(OptionEqualsSomeToContains, OptionNotEqualsSomeToNotContains)
}

object OptionEqualsSomeToContains extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case _ if expr.scalaLanguageLevel.exists(_ <= ScalaLanguageLevel.Scala_2_10) => None
    case qual `==` `scalaSome`(elem) if isOption(qual) =>
      Some(replace(expr).withText(s"${qual.getText}.contains(${elem.getText})").highlightAll)
    case `scalaSome`(elem) `==` qual if isOption(qual) =>
      Some(replace(expr).withText(s"${qual.getText}.contains(${elem.getText})").highlightAll)
    case _ => None
  }
}

object OptionNotEqualsSomeToNotContains extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.not.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case _ if expr.scalaLanguageLevel.exists(_ <= ScalaLanguageLevel.Scala_2_10) => None
    case qual `!=` `scalaSome`(elem) if isOption(qual) =>
      Some(replace(expr).withText(s"!${qual.getText}.contains(${elem.getText})").highlightAll)
    case `scalaSome`(elem) `!=` qual if isOption(qual) =>
      Some(replace(expr).withText(s"!${qual.getText}.contains(${elem.getText})").highlightAll)
    case _ => None
  }
}
