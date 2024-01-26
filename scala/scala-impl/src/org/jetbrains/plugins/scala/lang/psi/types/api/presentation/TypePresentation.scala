package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

trait TypePresentation {

  def typeText(
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

  final def canonicalText(`type`: ScType, context: TypePresentationContext): String = {
    val renderer: NameRenderer = new NameRenderer {
      override def renderName(e: PsiNamedElement): String = renderNameImpl(e, withPoint = false)
      override def renderNameWithPoint(e: PsiNamedElement): String = renderNameImpl(e, withPoint = true)

      private def renderNameImpl(e: PsiNamedElement, withPoint: Boolean): String = {
        val str = e match {
          case c: PsiClass =>
            val qname = c.qualifiedName
            if (qname == null || (!ScalaApplicationSettings.PRECISE_TEXT && qname == c.name)) c.name // SCL-21184
            else "_root_." + qname
          case p: PsiPackage =>
            "_root_." + p.getQualifiedName
          case _ =>
            e.nameContext match {
              case m: ScMember =>
                m.containingClass match {
                  case o: ScObject =>
                    (if (ScalaApplicationSettings.PRECISE_TEXT && o.isStatic) "_root_." else "") +
                      renderNameImpl(o, withPoint = true) + e.name // SCL-21182
                  case _ => m.getParent match {
                    case p: ScPackaging if ScalaApplicationSettings.PRECISE_TEXT => "_root_." + p.fullPackageName + "." + e.name // SCL-21514
                    case _ => e.name
                  }
                }
              case _ => e.name
            }
        }
        val res = removeKeywords(str)
        if (res.nonEmpty && withPoint) res + "." else res
      }
    }
    typeText(`type`, renderer, PresentationOptions(renderStdTypes = ScalaApplicationSettings.PRECISE_TEXT, canonicalForm = true))(context)
  }
}

object TypePresentation {

  val ABSTRACT_TYPE_POSTFIX = "_"

  private val PredefinedPackages = Set("scala.Predef", "scala")
  def isPredefined(td: ScTypeDefinition): Boolean =
    PredefinedPackages.contains(td.qualifiedName)

  private def removeKeywords(text: String): String =
    ScalaNamesUtil.escapeKeywordsFqn(text)

  case class PresentationOptions(
    renderProjectionTypeName: Boolean = false,
    renderStdTypes: Boolean = false,
    renderInfixType: Boolean = false,
    canonicalForm: Boolean = false // Canonical renderer is sometimes not enough, SCL-21183
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