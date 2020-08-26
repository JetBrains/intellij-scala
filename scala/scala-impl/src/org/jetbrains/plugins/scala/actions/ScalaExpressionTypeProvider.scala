package org.jetbrains.plugins.scala.actions

import java.{util => ju}

import com.intellij.codeInsight.documentation.DocumentationComponent
import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.ColorUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.jdk.CollectionConverters._

class ScalaExpressionTypeProvider extends ExpressionTypeProvider[PsiElement] {

  import ScalaExpressionTypeProvider._

  override def getErrorHint: String = ScalaBundle.message("no.expression.found")

  override def getExpressionsAt(elementAt: PsiElement): ju.List[PsiElement] = {
    @scala.annotation.tailrec
    def collectCandidates(parents: Iterator[PsiElement], acc: List[PsiElement] = Nil): List[PsiElement] = {
      if (parents.hasNext) {
        val current = parents.next()
        current match {
          case (block: ScBlock) childOf (_: ScArgumentExprList | _: ScCaseClause) =>
            if (block.statements.size == 1) collectCandidates(parents, acc)
            else                            collectCandidates(parents, current :: acc)
          case _: ScFunctionDefinition | _: ScMacroDefinition                        => current :: acc
          case _: ScTemplateBody | _: ScBlock | _: ScValueOrVariable                 => acc
          case _: ScExpression | _: ScBindingPattern                                 => collectCandidates(parents,  current :: acc)
          case _                                                                     => collectCandidates(parents, acc)
        }
      } else acc
    }

    collectCandidates(elementAt.withParentsInFile).reverse.asJava
  }

  override def getInformationHint(element: PsiElement): String =
    extractType(element).fold(unknownType) { (t: ScType) =>
      val original  = ScalaBundle.message("type.hint.table.title.type")      -> t
      val dealiased = ScalaBundle.message("type.hint.table.title.dealiased") -> t.removeAliasDefinitions()
      val widened   = ScalaBundle.message("type.hint.table.title.widened")   -> t.tryExtractDesignatorSingleton

      val (expected, withoutImplicits) = element match {
        case expr: ScExpression =>
          val expected         = expr.expectedType().map(ScalaBundle.message("type.hint.table.title.expected") -> _)
          val withoutImplicits = expr.getTypeWithoutImplicits().toOption.map(ScalaBundle.message("type.hint.table.title.without.implicits") -> _)
          (expected, withoutImplicits)
        case _                  => (None, None)
      }

      val infos = (Seq(original, dealiased, widened) ++ expected ++ withoutImplicits)
        .map { case (title, tpe) => title -> tpe.presentableText(element) }
        .distinctBy(_._2)

      infos
        .map { case (title, tpeText) => makeAdvancedInformationTableRow(title, tpeText) }
        .mkString("<table>", "\n", "</table>")
    }
}

object ScalaExpressionTypeProvider {
  private def unknownType = ScalaBundle.message("unknown.type")

  private def extractType: PsiElement => Option[ScType] = {
    case element @ ResolvedWithSubst(target, subst) =>
      target.ofNamedElement(subst, scalaScope = Some(element.elementScope))
    case Typeable(tpe)              => Option(tpe)
    case _                          => None
  }

  private def makeAdvancedInformationTableRow(
    title:   String,
    tpeText: String
  ): String = {
    val titleCell = "<td align='left' valign='top' style='color:" +
      ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR) + "'>" +
      StringUtil.escapeXmlEntities(title) + ":</td>"

    val contentCell = s"<td>${StringUtil.escapeXmlEntities(tpeText)}</td>"
    s"<tr>$titleCell$contentCell</tr>"
  }

  def getTypeInfoHint(e: PsiElement): Option[String] =
    if (e.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)
      e.parent.flatMap(extractType).map(_.presentableText(e))
    else None
}
