package org.jetbrains.plugins.scala.actions.implicitConversions

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.{SimpleColoredComponent, SimpleTextAttributes}
import com.intellij.util.TextWithIcon
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.actions.Parameters
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypeAnnotationRenderer.ParameterTypeDecorateOptions
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._
import org.jetbrains.plugins.scala.lang.psi.types.result._

import java.awt.{BorderLayout, Component, Container}
import javax.swing._
import scala.annotation.nowarn

private class ScImplicitFunctionListCellRenderer(actual: PsiNamedElement)
  extends PsiElementListCellRenderer[PsiNamedElement] {

  override def getItemLocation(value: Any): TextWithIcon =
    value match {
      case e: ScMember if e.isSynthetic => super.getItemLocation(e.syntheticNavigationElement)
      case _                            => super.getItemLocation(value)
    }

  override def getListCellRendererComponent(list: JList[_], value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
    val foregroundColor = Option(EditorColorsManager.getInstance().getGlobalScheme.getAttributes(DefaultHighlighter.IMPLICIT_CONVERSIONS))
      .getOrElse(DefaultHighlighter.IMPLICIT_CONVERSIONS.getDefaultAttributes)
      .getForegroundColor

    val tuple = value.asInstanceOf[Parameters]
    val item = tuple.newExpression
    val firstPart = tuple.elements

    val comp = super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
    comp match {
      case container: Container =>
        val components = container.getComponents
        if (components.size < 3) return container
        val colored = components(2).asInstanceOf[SimpleColoredComponent]
        if (item == actual) {
          colored.clear()
          colored.setIcon(actual.getIcon(0))
          colored.append(getElementText(actual), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }

        val color = if (firstPart.contains(item)) {
          if (isSelected) UIUtil.getListSelectionBackground(true) else foregroundColor
        } else {
          throw new RuntimeException("Implicit conversions list contains unknown value: " + item)
        }

        colored.setBackground(color)

        val maybeTextWithIcon = Option(getItemLocation(item))

        maybeTextWithIcon
          .foreach { textWithIcon =>
            val locationComponent = new JLabel(textWithIcon.getText, textWithIcon.getIcon, SwingConstants.RIGHT)
            locationComponent.setBackground(color)

            val spacer = new JPanel()
            spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2))
            spacer.setBackground(color)

            add(spacer, BorderLayout.CENTER)
            add(locationComponent, BorderLayout.EAST)
          }
      case _ =>
    }
    comp
  }

  @nowarn("cat=deprecation")
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
    ParameterTypeDecorateOptions.DecorateAll
  )

  private def typeRenderer: TypeRenderer =
    _.presentableText(TypePresentationContext.emptyContext)

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
    renderImplicitModifier = true,
    clausesSeparator = ""
  )

  private def textEscaper: TextEscaper = TextEscaper.Html

  override def getIconFlags: Int = 0

  override def getContainerText(element: PsiNamedElement, name: String): String = null //todo: add package name
}