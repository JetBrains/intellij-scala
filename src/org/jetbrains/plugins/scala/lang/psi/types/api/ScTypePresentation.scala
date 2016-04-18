package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage}
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.light.scala.ScLightTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author adkozlov
  */
trait ScTypePresentation extends TypeSystemOwner {
  final def presentableText(`type`: ScType) = typeText(`type`, {
    case c: PsiClass => ScalaPsiUtil.nameWithPrefixIfNeeded(c)
    case e => e.name
  }, {
    case o: ScObject if Set("scala.Predef", "scala").contains(o.qualifiedName) => ""
    case p: PsiPackage => ""
    case c: PsiClass => ScalaPsiUtil.nameWithPrefixIfNeeded(c) + "."
    case e => e.name + "."
  }
  )

  final def urlText(`type`: ScType) = {
    def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      e match {
        case o: ScObject if withPoint && o.qualifiedName == "scala.Predef" => ""
        case e: PsiClass => "<a href=\"psi_element://" + e.qualifiedName + "\"><code>" +
          StringEscapeUtils.escapeHtml(e.name) +
          "</code></a>" + (if (withPoint) "." else "")
        case pack: PsiPackage if withPoint => ""
        case _ => StringEscapeUtils.escapeHtml(e.name) + "."
      }
    }
    typeText(`type`, nameFun(_, withPoint = false), nameFun(_, withPoint = true))
  }

  final def canonicalText(`type`: ScType) = {
    def removeKeywords(s: String): String = {
      s.split('.').map(s => if (ScalaNamesUtil.isKeyword(s)) "`" + s + "`" else s).mkString(".")
    }
    def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      removeKeywords(e match {
        case c: PsiClass =>
          val qname = c.qualifiedName
          if (qname != null && qname != c.name /* exlude default package*/ ) "_root_." + qname else c.name
        case p: PsiPackage => "_root_." + p.getQualifiedName
        case _ =>
          ScalaPsiUtil.nameContext(e) match {
            case m: ScMember =>
              m.containingClass match {
                case o: ScObject => nameFun(o, withPoint = true) + e.name
                case _ => e.name
              }
            case _ => e.name
          }
      }) + (if (withPoint) "." else "")
    }
    typeText(`type`, nameFun(_, withPoint = false), nameFun(_, withPoint = true))
  }

  protected def typeText(`type`: ScType,
                         nameFun: PsiNamedElement => String,
                         nameWithPointFun: PsiNamedElement => String): String
}

object ScTypePresentation {
  val ABSTRACT_TYPE_PREFIX = "_"

  def different(t1: ScType, t2: ScType): (String, String) = {
    val (p1, p2) = (t1.presentableText, t2.presentableText)
    if (p1 != p2) (p1, p2)
    else (t1.canonicalText.replace("_root_.", ""), t2.canonicalText.replace("_root_.", ""))
  }

  def shouldExpand(ta: ScTypeAliasDefinition): Boolean = ta match {
    case _: ScLightTypeAliasDefinition | childOf(_: ScRefinement) => true
    case _ =>
      ScalaPsiUtil.superTypeMembers(ta).exists(_.isInstanceOf[ScTypeAliasDeclaration])
  }

  def withoutAliases(`type`: ScType): String = {
    `type`.removeAliasDefinitions(expandableOnly = true).presentableText
  }
}

case class ScTypeText(tp: ScType) {
  val canonicalText = tp.canonicalText
  val presentableText = tp.presentableText
}
