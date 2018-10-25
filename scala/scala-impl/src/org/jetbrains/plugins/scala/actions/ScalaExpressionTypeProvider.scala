package org.jetbrains.plugins.scala.actions

import java.util

import com.intellij.codeInsight.documentation.DocumentationComponent
import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.ColorUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlock, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.collection.JavaConverters._

class ScalaExpressionTypeProvider extends ExpressionTypeProvider[PsiElement] {
  import ScalaExpressionTypeProvider._

  override def getErrorHint: String = "No expression found"

  override def getExpressionsAt(elementAt: PsiElement): util.List[PsiElement] =
    elementAt.withParentsInFile.takeWhile {
      case (_: ScBlock) childOf (_: ScArgumentExprList | _: ScCaseClause)                                         => true
      case _: ScTemplateBody | _: ScFunctionDefinition | _: ScMacroDefinition | _: ScBlock | _: ScValueOrVariable => false
      case _                                                                                                      => true
    }.filter {
      case ScBlock(_: ScExpression) => false
      case _: ScBindingPattern      => true
      case _: ScExpression          => true
      case _                        => false
    }.toList.asJava

  override def getInformationHint(element: PsiElement): String =
    extractType(element).fold(unknownType) { t: ScType =>
      val original  = "Type"      -> t
      val dealiased = "Dealiased" -> t.removeAliasDefinitions()
      val widened   = "Widened"   -> t.tryExtractDesignatorSingleton

      val (expected, withoutImplicits) = element match {
        case expr: ScExpression =>
          val expected         = expr.expectedType().map("Expected" -> _)
          val withoutImplicits = expr.getTypeWithoutImplicits().toOption.map("Without implicits" -> _)
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
  private val unknownType = "<unknown>"

  private def extractType(e: PsiElement): Option[ScType] = e match {
    case ResolvedWithSubst(target, subst) => target.ofNamedElement(subst, scalaScope = Option(e.elementScope))
    case Typeable(tpe)                    => Option(tpe)
    case _                                => None
  }

  private def makeAdvancedInformationTableRow(
    title:   String,
    tpeText: String
  ): String = {
    val titleCell = "<td align='left' valign='top' style='color:" +
      ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR) + "'>" +
      StringUtil.escapeXml(title) + ":</td>"

    val contentCell = s"<td>$tpeText</td>"
    s"<tr>$titleCell$contentCell</tr>"
  }

  def getTypeInfoHint(e: PsiElement): Option[String] =
    if (e.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)
      e.parent.flatMap(extractType).map(_.presentableText(e))
    else None
}
