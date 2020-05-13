package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt, childOf}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author adkozlov
  */
trait TypePresentation {

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
    typeText(`type`, renderer)
  }

  final def urlText(`type`: ScType): String = {
    import StringEscapeUtils.escapeHtml
    import HtmlUtils._

    val renderer: NameRenderer = new NameRenderer {
      override def escapeName(e: String): String = escapeHtml(e)
      override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)
      override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

      private def nameFun(e: PsiNamedElement, withPoint: Boolean): String =
        e match {
          // TODO: shouldn't "scala" also be checked  like in `presentableText`? see changes of ScTypePresentation.scala in SCL-4493
          case o: ScObject if withPoint && o.qualifiedName == "scala.Predef" => ""
          case e: PsiClass                => psiRef(e.qualifiedName)(code(e.name)) + pointOptStr(withPoint)
          case _: PsiPackage if withPoint => ""
          case _                          => escapeHtml(e.name) + pointOptStr(withPoint)
        }
    }
    typeText(`type`, renderer)(TypePresentationContext.emptyContext)
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
        removeKeywords(str) + pointOptStr(withPoint)
      }
    }
    typeText(`type`, renderer)(TypePresentationContext.emptyContext)
  }

  protected def typeText(`type`: ScType, nameRenderer: NameRenderer)
                        (implicit context: TypePresentationContext): String
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

  private def pointOptStr(withPoint: Boolean): String =
    if (withPoint) "." else ""

  private def removeKeywords(text: String): String =
    ScalaNamesUtil.escapeKeywordsFqn(text)

  trait NameRenderer {
    def renderName(e: PsiNamedElement): String
    def renderNameWithPoint(e: PsiNamedElement): String
    def escapeName(name: String): String = name
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

  def upperBoundText(maybeType: TypeResult)
                    (toString: ScType => String): String =
    upperBoundText(maybeType.toOption)(toString)

  def upperBoundText(`type`: ScType)
                    (toString: ScType => String): String =
    upperBoundText(Some(`type`))(toString)

  def lowerBoundText(maybeType: TypeResult)
                    (toString: ScType => String): String =
    lowerBoundText(maybeType.toOption)(toString)

  def lowerBoundText(`type`: ScType)
                    (toString: ScType => String): String =
    lowerBoundText(Some(`type`))(toString)

  import ScalaTokenTypes.{tLOWER_BOUND, tUPPER_BOUND}

  private[this] def upperBoundText(maybeType: Option[ScType])
                                  (toString: ScType => String): String =
    boundText(maybeType, tUPPER_BOUND)(_.isAny, toString)

  private[this] def lowerBoundText(maybeType: Option[ScType])
                                  (toString: ScType => String): String =
    boundText(maybeType, tLOWER_BOUND)(_.isNothing, toString)

  private[this] def boundText(maybeType: Option[ScType], bound: IElementType)
                             (predicate: ScType => Boolean, toString: ScType => String) =
    maybeType.collect {
      case t if !predicate(t) => " " + bound + " " + toString(t)
    }.getOrElse("")

}

case class ScTypeText(tp: ScType)(implicit tpc: TypePresentationContext) {
  val canonicalText: String = tp.canonicalText
  val presentableText: String = tp.presentableText
}
