package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation.PresentationOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{NameRenderer, TypePresentation, TypeRenderer}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

private [documentationProvider] object ScalaDocTypeRenderer {
  import org.apache.commons.lang.StringEscapeUtils.escapeHtml
  import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils._

  private val annotationsRenderer = new NameRenderer {
    override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)

    override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

    private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      val res = e match {
        case clazz: PsiClass =>
          clazz.qualifiedNameOpt
            .fold(escapeName(clazz.name))(_ => classLinkWithLabel(clazz, clazz.name, defLinkHighlight = false, isAnnotation = true))
        case _ =>
          psiElement(e, Some(e.name))
      }
      res + TypePresentation.pointStr(withPoint && res.nonEmpty)
    }
  }

  private val renderer: NameRenderer = new NameRenderer {
    override def escapeName(e: String): String = escapeHtml(e)

    override def renderName(e: PsiNamedElement): String = nameFun(e, withPoint = false)

    override def renderNameWithPoint(e: PsiNamedElement): String = nameFun(e, withPoint = true)

    private def nameFun(e: PsiNamedElement, withPoint: Boolean): String = {
      val res = e match {
        case o: ScObject if withPoint && TypePresentation.isPredefined(o) => ""
        case _: PsiPackage if withPoint => ""
        case clazz: PsiClass =>
          clazz.qualifiedNameOpt
            .fold(escapeName(clazz.name))(_ => classLinkWithLabel(clazz, clazz.name, defLinkHighlight = false))
        case a: ScTypeAlias =>
          a.qualifiedNameOpt
            .fold(escapeHtml(e.name))(psiElementLink(_, e.name, attributesKey = Some(DefaultHighlighter.TYPE_ALIAS)))
        case _ =>
          psiElement(e, Some(e.name))
      }
      res + TypePresentation.pointStr(withPoint && res.nonEmpty)
    }
  }

  private val options = PresentationOptions(
    renderProjectionTypeName = true,
    renderStdTypes = true,
    renderInfixType = true
  )

  def apply(originalElement: Option[PsiElement]): TypeRenderer = {
    val presentableContext =
      originalElement.fold(TypePresentationContext.emptyContext)(TypePresentationContext.psiElementPresentationContext)
    _.typeText(renderer, options)(presentableContext)
  }

  def forAnnotations(originalElement: Option[PsiElement]): TypeRenderer = {
    val presentableContext =
      originalElement.fold(TypePresentationContext.emptyContext)(TypePresentationContext.psiElementPresentationContext)
    _.typeText(annotationsRenderer, options)(presentableContext)
  }

  def apply(originalElement: PsiElement, substitutor: ScSubstitutor): TypeRenderer =
    substitutor(_).typeText(renderer, options)(originalElement)
}
