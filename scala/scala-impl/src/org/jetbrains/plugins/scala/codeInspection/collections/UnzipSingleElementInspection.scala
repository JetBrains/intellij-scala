package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.codeInspection.collections.UnzipSingleElementInspection._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

import scala.collection.immutable.ArraySeq

class UnzipSingleElementInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(UnzipSingleElement)
}

private object UnzipSingleElementInspection {
  private val UnzipSingleElement: SimplificationType = new SimplificationType {

    override def hint: String = ScalaInspectionBundle.message("replace.with.map")

    override def getSimplification(e: ScExpression): Option[Simplification] = Some(e).collect {
      // TODO infix notation?
      case `._1`(`.unzip`(q)) => (q, 1)
      case `._2`(`.unzip`(q)) => (q, 2)
      case `._1`(`.unzip3`(q) ) => (q, 1)
      case `._2`(`.unzip3`(q)) => (q, 2)
      case `._3`(`.unzip3`(q)) => (q, 3)
    }.map { case (q, index) =>
      val args = createExpressionFromText(s"_._$index")(q.getContext)
      val text = invocationText(q, "map", args)
      replace(e).withText(text).highlightFrom(q)
    }

    private val `._1` = invocation("_1").from(ArraySeq("scala.Tuple2", "scala.Tuple3"))
    private val `._2` = invocation("_2").from(ArraySeq("scala.Tuple2", "scala.Tuple3"))
    private val `._3` = invocation("_3").from(ArraySeq("scala.Tuple3"))
  }

}
