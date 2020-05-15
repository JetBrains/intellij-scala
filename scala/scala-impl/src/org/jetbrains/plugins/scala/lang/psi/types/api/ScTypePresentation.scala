package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiNamedElementExt, childOf}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author adkozlov
 */
trait TypePresentation {

  protected def typeText(
    `type`: ScType,
    nameRenderer: NameRenderer,
    options: PresentationOptions = PresentationOptions.Default
  )(implicit context: TypePresentationContext): String

  final def presentableText(`type`: ScType, withPrefix: Boolean = true)
                           (implicit context: TypePresentationContext): String = {
    val renderer = new NameRenderer {
      override def renderName(e: PsiNamedElement): String = e match {
        case c: PsiClass if withPrefix => ScalaPsiUtil.nameWithPrefixIfNeeded(c)
        case e                         => e.name
      }
      override def renderNameWithPoint(e: PsiNamedElement): String = e match {
        case o: ScObject if isPredefined(o) => ""
        case _: PsiPackage                  => ""
        case c: PsiClass                    => ScalaPsiUtil.nameWithPrefixIfNeeded(c) + "."
        case e                              => e.name + "."
      }
    }
    typeText(`type`, renderer, PresentationOptions.Default)
  }

  // For now only used in `documentationProvider` package
  final def urlText(`type`: ScType): String = {
    import HtmlUtils._
    import StringEscapeUtils.escapeHtml

    val renderer: NameRenderer = new NameRenderer {
      override def escapeName(e: String): String = escapeHtml(e)
      override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)
      override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

      private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
        val res = e match {
          case o: ScObject if withPoint && isPredefined(o) => ""
          case _: PsiPackage if withPoint                  => ""
          case e: PsiClass                                 => psiRef(e.qualifiedName)(code(e.name))
          case a: ScTypeAlias                              => a.qualifiedNameOpt.fold(escapeHtml(e.name))(psiRef(_)(code(e.name)))
          case _                                           => escapeHtml(e.name)
        }
        res + pointStr(withPoint && res.nonEmpty)
      }
    }

    val options = PresentationOptions(
      expandTypeParameterBounds = true,
      renderProjectionTypeName = true,
      renderValueTypes = true
    )
    typeText(`type`, renderer, options)(TypePresentationContext.emptyContext)
  }

  final def canonicalText(`type`: ScType): String = {
    val renderer: NameRenderer = new NameRenderer {
      override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)
      override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

      private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
        val str = e match {
          case c: PsiClass =>
            val qname = c.qualifiedName
            if (qname == null || qname == c.name) c.name
            else "_root_." + qname
          case p: PsiPackage =>
            "_root_." + p.getQualifiedName
          case _ =>
            ScalaPsiUtil.nameContext(e) match {
              case m: ScMember =>
                m.containingClass match {
                  case o: ScObject => nameFun(o, withPoint = true) + e.name
                  case _ => e.name
                }
              case _ => e.name
            }
        }
        removeKeywords(str) + pointStr(withPoint)
      }
    }
    typeText(`type`, renderer, PresentationOptions.Default)(TypePresentationContext.emptyContext)
  }
}

object TypePresentation {

  private val PredefinedPackages = Set("scala.Predef", "scala")
  private def isPredefined(td: ScTypeDefinition): Boolean =
    PredefinedPackages.contains(td.qualifiedName)

  private object HtmlUtils {
    import StringEscapeUtils._
    def psiRef(fqn: String)(content: String): String =
      s"""<a href="psi_element://${escapeHtml(fqn)}">$content</a>"""
    def code(content: String): String =
      s"""<code>${StringEscapeUtils.escapeHtml(content)}</code>"""
  }

  private def pointStr(withPoint: Boolean): String =
    if (withPoint) "." else ""

  private def removeKeywords(text: String): String =
    ScalaNamesUtil.escapeKeywordsFqn(text)

  trait TextEscaper {
    def escape(text: String): String
  }
  object TextEscaper {
    object Html extends TextEscaper {
      override def escape(text: String): String = escapeHtml(text)
    }
  }
  trait NameRenderer extends TextEscaper {
    def renderName(e: PsiNamedElement): String
    def renderNameWithPoint(e: PsiNamedElement): String
    def escapeName(name: String): String = name
    override final def escape(text: String): String = escapeName(text)
  }
  object NameRenderer {

    object Noop extends NameRenderer {
      override def renderName(e: PsiNamedElement): String = e.name
      override def renderNameWithPoint(e: PsiNamedElement): String = e.name
    }
  }

  case class PresentationOptions(
    expandTypeParameterBounds: Boolean = false,
    renderProjectionTypeName: Boolean = false,
    renderValueTypes: Boolean = false
  )
  object PresentationOptions {
    val Default: PresentationOptions = PresentationOptions()
  }
}

object ScTypePresentation {
  val ABSTRACT_TYPE_POSTFIX = "_"

  // TODO Why the presentable text for java.lang.Long is "Long" in Scala? (see SCL-15899)
  // TODO (and why the canonical text for scala.Long is "Long", for that matter)
  def different(t1: ScType, t2: ScType)
               (implicit context: TypePresentationContext): (String, String) = {
    val (p1, p2) = (t1.presentableText, t2.presentableText)
    if (p1 != p2) (p1, p2)
    else (t1.canonicalText.replace("_root_.", ""), t2.canonicalText.replace("_root_.", ""))
  }

  def shouldExpand(typeAlias: ScTypeAliasDefinition): Boolean = typeAlias match {
    case childOf(_, _: ScRefinement) => true
    case _ => ScalaPsiUtil.superTypeMembers(typeAlias).exists(_.isInstanceOf[ScTypeAliasDeclaration])
  }

  def withoutAliases(`type`: ScType)
                    (implicit context: TypePresentationContext): String =
    `type`.removeAliasDefinitions(expandableOnly = true).presentableText
}

case class ScTypeText(tp: ScType)(implicit tpc: TypePresentationContext) {
  val canonicalText: String = tp.canonicalText
  val presentableText: String = tp.presentableText
}

class TypeBoundsRenderer(textEscaper: TextEscaper) {

  import ScalaTokenTypes.{tLOWER_BOUND, tUPPER_BOUND}

  def upperBoundText(typ: ScType)
                    (toString: ScType => String): String =
    if (typ.isAny) ""
    else boundText(typ, tUPPER_BOUND)(toString)

  def lowerBoundText(typ: ScType)
                    (toString: ScType => String): String =
    if (typ.isNothing) ""
    else boundText(typ, tLOWER_BOUND)(toString)

  def boundText(typ: ScType, bound: IElementType)
               (toString: ScType => String): String = {
    val boundEscaped = textEscaper.escape(bound.toString)
    " " + boundEscaped + " " + toString(typ)
  }

  def render(
    paramName: String,
    lower: Option[ScType],
    upper: Option[ScType],
    view: Seq[ScType],
    context: Seq[ScType]
  )(toString: ScType => String): String = {
    val buffer = new StringBuilder

    buffer ++= paramName

    def append(text: String): Unit =
      buffer ++= text

    lower.foreach(b => append(lowerBoundText(b)(toString)))
    upper.foreach(b => append(upperBoundText(b)(toString)))
    view.foreach(b => append(boundText(b, ScalaTokenTypes.tVIEW)(toString)))
    context.foreach(b => append(boundText(b, ScalaTokenTypes.tCOLON)(toString)))

    buffer.result
  }
}