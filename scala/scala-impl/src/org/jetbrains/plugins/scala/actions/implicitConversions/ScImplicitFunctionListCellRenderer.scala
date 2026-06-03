package org.jetbrains.plugins.scala.actions.implicitConversions

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.TextWithIcon
import org.jetbrains.plugins.scala.actions.Parameters
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorator
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.result._

import java.awt.{Component, Font}
import javax.swing._

private class ScImplicitFunctionListCellRenderer(actual: PsiNamedElement, place: PsiElement)
  extends PsiElementListCellRenderer[PsiNamedElement] {

  override def getItemLocation(value: Any): TextWithIcon =
    value match {
      case e: ScMember if e.isSynthetic => super.getItemLocation(e.syntheticNavigationElement)
      case _                            => super.getItemLocation(value)
    }

  override def getNavigationItemAttributes(value: Any): TextAttributes = {
    val result = super.getNavigationItemAttributes(value)
    if (value == actual) {
      val attributes = Option(result).getOrElse(new TextAttributes())
      attributes.setFontType(Font.BOLD)
      return attributes
    }
    result
  }

  override def getListCellRendererComponent(list: JList[_], value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
    val item = value.asInstanceOf[Parameters].newExpression
    super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
  }

  override def getElementText(element: PsiNamedElement): String =
    element match {
      case method: ScFunction  => functionRenderer.render(method)
      case b: ScBindingPattern => b.name + ": " + typeRenderer.render(b.`type`().getOrAny)
      case _                   => element.name
    }

  private def functionRenderer = new FunctionRenderer(
    typeParamsRenderer = None,
    paramsRenderer,
    typeAnnotationRenderer,
    renderDefKeyword = false
  )

  private def typeAnnotationRenderer = new TypeAnnotationRenderer(
    typeRenderer,
    ParameterTypeDecorator.DecorateAll
  )

  private def typeRenderer: TypeRenderer =
    _.presentableText(place, Context(place))

  private def paramRenderer = new ParameterRenderer(
    typeRenderer,
    ModifiersRenderer.SimpleText(TextEscaper.Html),
    typeAnnotationRenderer,
    textEscaper,
    withMemberModifiers = true,
    withAnnotations = true
  )

  private def paramsRenderer: ParametersRenderer = new ParametersRenderer(
    paramRenderer,
    shouldRenderImplicitModifier = true
  )

  private def textEscaper: TextEscaper = TextEscaper.Html

  override def getIconFlags: Int = 0

  override def getContainerText(element: PsiNamedElement, name: String): String = null //todo: add package name
}
