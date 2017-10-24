package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class MapKeysInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(MapKeys)
}

object MapKeys extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.keys")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.map`(`_._1`())`.toIterator`() if isMap(qual) =>
      val iteratorHint = InspectionBundle.message("replace.with.keysIterator")
      Some(replace(expr).withText(invocationText(qual, "keysIterator")).highlightFrom(qual).withHint(iteratorHint))
    case qual`.map`(`_._1`())`.toSet`() if isMap(qual) =>
      val setHint = InspectionBundle.message("replace.with.keySet")
      Some(replace(expr).withText(invocationText(qual, "keySet")).highlightFrom(qual).withHint(setHint))
    case qual`.map`(`_._1`()) if isMap(qual) =>
      Some(replace(expr).withText(invocationText(qual, "keys")).highlightFrom(qual))
    case _ => None
  }
}
