package org.jetbrains.plugins.scala.actions.implicitConvertions

import java.awt.{BorderLayout, Component, Container}

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.{SimpleColoredComponent, SimpleTextAttributes}
import com.intellij.util.ui.UIUtil
import javax.swing._
import org.jetbrains.plugins.scala.actions.Parameters
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.DefaultListCellRendererAdapter
import org.jetbrains.plugins.scala.util.JListCompatibility

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2010
 */
private class ScImplicitFunctionListCellRenderer(actual: PsiNamedElement)
  extends ScImplicitFunctionListCellRendererAdapter {

  override def getListCellRendererComponentAdapter(containter: JListCompatibility.JListContainer,
                                                   value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
    val foregroundColor = Option(EditorColorsManager.getInstance().getGlobalScheme.getAttributes(DefaultHighlighter.IMPLICIT_CONVERSIONS))
      .getOrElse(DefaultHighlighter.IMPLICIT_CONVERSIONS.getDefaultAttributes)
      .getForegroundColor

    val tuple = value.asInstanceOf[Parameters]
    val item = tuple.newExpression
    val firstPart = tuple.elements

    val comp = getSuperListCellRendererComponent(containter.getList, item, index, isSelected, cellHasFocus)
    comp match {
      case container: Container =>
        val colored = container.getComponents.apply(2).asInstanceOf[SimpleColoredComponent]
        if (item == actual) {
          colored.clear()
          colored.setIcon(actual.getIcon(0))
          colored.append(getElementText(actual), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }

        val color = if (firstPart.contains(item)) {
          if (isSelected) UIUtil.getListSelectionBackground else foregroundColor
        } else {
          throw new RuntimeException("Implicit conversions list contains unknown value: " + item)
        }

        colored.setBackground(color)

        val maybeRenderer = Option(getRightCellRenderer(item))

        maybeRenderer
          .map(DefaultListCellRendererAdapter.getListCellRendererComponent(_, containter.getList, item, index, isSelected, cellHasFocus))
          .foreach { component =>
            component.setBackground(color)
            add(component, BorderLayout.EAST)
          }

        maybeRenderer
          .map(_ => new JPanel)
          .foreach { spacer =>
            spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2))
            spacer.setBackground(color)
            add(spacer, BorderLayout.CENTER)
          }
      case _ =>
    }
    comp
  }

  override def getElementText(element: PsiNamedElement): String = {
    element match {
      case method: ScFunction =>
        method.name + PresentationUtil.presentationStringForPsiElement(method.paramClauses) + ": " +
          PresentationUtil.presentationStringForScalaType(method.returnType.getOrAny)
      case b: ScBindingPattern => b.name + ": " +
        PresentationUtil.presentationStringForScalaType(b.`type`().getOrAny)
      case _ =>
        element.name
    }
  }

  override def getIconFlags: Int = 0

  override def getContainerText(element: PsiNamedElement, name: String): String = null //todo: add package name
}