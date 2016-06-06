package org.jetbrains.plugins.scala.lang.transformation
package functions

import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandPlaceholderSyntax extends AbstractTransformer {
  def transformation = {
    case (e: ScUnderscoreSection) && Parent(_: ScExpression | _: ScArgumentExprList) =>
      val enclosure = e.parents.toStream.takeWhile(e => e.isInstanceOf[ScExpression] || e.isInstanceOf[ScArgumentExprList]).last

      val (placeholders, typeElements) = enclosure.depthFirst.collect {
        case (_: ScUnderscoreSection) && Parent((typed: ScTypedStmt) && Parent(it: ScParenthesisedExpr)) => (it, typed.typeElement)
        case (_: ScUnderscoreSection) && Parent(typed: ScTypedStmt) => (typed, typed.typeElement)
        case it: ScUnderscoreSection => (it, None)
      }.toVector.unzip

      val count = placeholders.length
      val singleParameter = count == 1
      val ids = if (singleParameter) Seq("x") else Range(1, count + 1).map("x" + _)

      placeholders.zip(ids).foreach(p => p._1.replace(code"${p._2}"))

      val parameters = if (singleParameter && typeElements.head.isEmpty) ids.head else {
        val typedIds = ids.zip(typeElements).map(p => p._2.map(p._1 + ": " + _.text).getOrElse(p._1))
        typedIds.mkString("(", ", ", ")")
      }

      // TODO don't re-parse type elements
      enclosure.replace(code"$parameters => $enclosure")
  }
}
