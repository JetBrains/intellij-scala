package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MapKeysInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(MapKeys)
}

object MapKeys extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.keys")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.toIterator`(qual`.map` `_._1`()) if isMap(qual) =>
      val iteratorHint = ScalaInspectionBundle.message("replace.with.keysIterator")
      Some(replace(expr).withText(invocationText(qual, "keysIterator")).highlightFrom(qual).withHint(iteratorHint))
    case `.toSet`(qual`.map` `_._1`()) if isMap(qual) =>
      val setHint = ScalaInspectionBundle.message("replace.with.keySet")
      Some(replace(expr).withText(invocationText(qual, "keySet")).highlightFrom(qual).withHint(setHint))
    case qual`.map` `_._1`() if isMap(qual) =>
      Some(replace(expr).withText(invocationText(qual, "keys")).highlightFrom(qual))
    case _ => None
  }
}
