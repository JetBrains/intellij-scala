package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

/**
 * @author Nikolay.Tropin
 */
class OptionEqualsSomeToContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(OptionEqualsSomeToContains, OptionNotEqualsSomeToNotContains)
}

object OptionEqualsSomeToContains extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.contains")

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
  override def hint: String = InspectionBundle.message("replace.with.not.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual `!=` `scalaSome`(elem) if isOption(qual) =>
      Some(replace(expr).withText(s"!${qual.getText}.contains(${elem.getText})").highlightAll)
    case `scalaSome`(elem) `!=` qual if isOption(qual) =>
      Some(replace(expr).withText(s"!${qual.getText}.contains(${elem.getText})").highlightAll)
    case _ => None
  }
}
