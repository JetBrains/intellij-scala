package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiNamedElementExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation._
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.NameRenderer
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.{HtmlPsiUtils, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * TODO: move to presentation package
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
    typeText(`type`, renderer, PresentationOptions.Default)(context)
  }

  // For now only used in `documentationProvider` package
  final def urlText(`type`: ScType)
                   (implicit context: TypePresentationContext): String = {
    import StringEscapeUtils.escapeHtml

    val renderer: NameRenderer = new NameRenderer {
      override def escapeName(e: String): String = escapeHtml(e)
      override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)
      override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

      private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
        import HtmlPsiUtils._
        val res = e match {
          case o: ScObject if withPoint && isPredefined(o) => ""
          case _: PsiPackage if withPoint                  => ""
          case clazz: PsiClass                             => classLinkSafe(clazz).getOrElse(escapeName(clazz.name))
          case a: ScTypeAlias                              => a.qualifiedNameOpt.fold(escapeHtml(e.name))(psiElementLink(_, e.name))
          case _                                           => escapeName(e.name)
        }
        res + pointStr(withPoint && res.nonEmpty)
      }
    }

    val options = PresentationOptions(
      renderProjectionTypeName = true,
      renderValueTypes = true,
      renderInfixType = true
    )
    typeText(`type`, renderer, options)(context)
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

// TODO: remove, this is left to minimize change diff
@deprecated("use TypePresentation instead")
object ScTypePresentation {

  val ABSTRACT_TYPE_POSTFIX: String =
    TypePresentation.ABSTRACT_TYPE_POSTFIX

  def different(t1: ScType, t2: ScType)
               (implicit context: TypePresentationContext): (String, String) =
    TypePresentation.different(t1, t2)

  def shouldExpand(typeAlias: ScTypeAliasDefinition): Boolean =
    TypePresentation.shouldExpand(typeAlias)

  def withoutAliases(`type`: ScType)
                    (implicit context: TypePresentationContext): String =
    TypePresentation.withoutAliases(`type`)
}

object TypePresentation {

  val ABSTRACT_TYPE_POSTFIX = "_"

  private val PredefinedPackages = Set("scala.Predef", "scala")
  private def isPredefined(td: ScTypeDefinition): Boolean =
    PredefinedPackages.contains(td.qualifiedName)

  private def pointStr(withPoint: Boolean): String =
    if (withPoint) "." else ""

  private def removeKeywords(text: String): String =
    ScalaNamesUtil.escapeKeywordsFqn(text)

  case class PresentationOptions(
    renderProjectionTypeName: Boolean = false,
    renderValueTypes: Boolean = false,
    renderInfixType: Boolean = false
  )
  object PresentationOptions {
    val Default: PresentationOptions = PresentationOptions()
  }

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