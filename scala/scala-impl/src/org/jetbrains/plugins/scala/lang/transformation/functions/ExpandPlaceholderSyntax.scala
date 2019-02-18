package org.jetbrains.plugins.scala.lang.transformation
package functions

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandPlaceholderSyntax extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScUnderscoreSection) && Parent(_: ScExpression | _: ScArgumentExprList) =>
      val enclosure = e.parents.toStream.takeWhile(e => e.isInstanceOf[ScExpression] || e.isInstanceOf[ScArgumentExprList]).last

      val (placeholders, typeElements) = enclosure.depthFirst().collect {
        case (_: ScUnderscoreSection) && Parent((typed: ScTypedExpression) && Parent(it: ScParenthesisedExpr)) => (it, typed.typeElement)
        case (_: ScUnderscoreSection) && Parent(typed: ScTypedExpression) => (typed, typed.typeElement)
        case it: ScUnderscoreSection => (it, None)
      }.toVector.unzip

      val count = placeholders.length
      val singleParameter = count == 1
      val names = name(placeholders)

      placeholders.zip(names).foreach(p => p._1.replace(code"${p._2}"))

      val parameters = if (singleParameter && typeElements.head.isEmpty) names.head else {
        val typedIds = names.zip(typeElements).map(p => p._2.map(p._1 + ": " + _.getText).getOrElse(p._1))
        typedIds.mkString("(", ", ", ")")
      }

      // TODO don't re-parse type elements
      enclosure.replace(code"$parameters => $enclosure")
  }

  private def name(expressions: Seq[ScExpression]): Seq[String] = {
    val names = expressions.map(name)
    val repeatedNames = repeated(names)

    val (_, result) = names.foldLeft((Map.empty[String, Int], Seq.empty[String])) { case ((usages, result), s) =>
      if (repeatedNames.contains(s)) {
        val i = usages.getOrElse(s, 0) + 1
        (usages.updated(s, i), result :+ (s + i))
      } else {
        (usages, result :+ s)
      }
    }

    result
  }

  private def name(e: ScExpression): String =
    NameSuggester.suggestNames(e).headOption.filterNot(_ == "value").getOrElse("x")

  private def repeated[T](xs: Seq[T]): Set[T] = xs.groupBy(identity).filter(_._2.length > 1).keySet
}
