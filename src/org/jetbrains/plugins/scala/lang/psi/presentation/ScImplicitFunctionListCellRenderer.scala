package org.jetbrains.plugins.scala.lang.psi.presentation

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import java.awt.{BorderLayout, Component, Color, Container}
import com.intellij.ui.{SimpleTextAttributes, SimpleColoredComponent}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import com.intellij.ide.util.PsiElementListCellRenderer
import javax.swing._
import com.intellij.psi.PsiNamedElement
import java.lang.String
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.actions.Parameters

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.06.2010
 */
class ScImplicitFunctionListCellRenderer(actual: PsiNamedElement) extends PsiElementListCellRenderer[PsiNamedElement] {
  final val darkBlueColor: Color = new Color(187, 223, 255)
  final val lightBlueColor: Color = new Color(223, 240, 255)

  override def getListCellRendererComponent(list: JList, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean) = {
    val tuple = value.asInstanceOf[Parameters]
    val item = tuple.getNewExpression
    val firstPart = tuple.getFirstPart
    val secondPart = tuple.getSecondPart
    val comp = super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
    comp match {
      case container: Container => {
        val colored = container.getComponents.apply(2).asInstanceOf[SimpleColoredComponent]
        if (item == actual) {
          colored.clear()
          colored.setIcon(actual.getIcon(0))
          colored.append(getElementText(actual), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        }

        if (firstPart.contains(item)) {
          colored.setBackground(if (isSelected) UIUtil.getListSelectionBackground else darkBlueColor)
        } else if (secondPart.contains(item)) {
          colored.setBackground(if (isSelected) UIUtil.getListSelectionBackground else lightBlueColor)
        } else {
          throw new RuntimeException("Implicit conversions list contains unknown value: " + item)
        }

        val rightRenderer: DefaultListCellRenderer = getRightCellRenderer(item)
        if (rightRenderer != null) {
          val rightCellRendererComponent: Component =
            rightRenderer.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
          val color: Color = isSelected match {
            case true => UIUtil.getListSelectionBackground
            case false if (firstPart.contains(item)) => darkBlueColor
            case false if (secondPart.contains(item)) => lightBlueColor
            case _ => throw new RuntimeException("Implicit conversions list contains unknown value: " + item)
          }
          rightCellRendererComponent.setBackground(color)
          add(rightCellRendererComponent, BorderLayout.EAST)
          val spacer: JPanel = new JPanel
          spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2))
          spacer.setBackground(color)
          add(spacer, BorderLayout.CENTER)
        }
      }
      case _ =>
    }
    comp
  }

  override def getElementText(element: PsiNamedElement) = {
    element match {
      case method: ScFunction => {
        method.name + PresentationUtil.presentationString(method.paramClauses) + ": " +
                PresentationUtil.presentationString(method.returnType.
                        getOrAny)
      }
      case b: ScBindingPattern => b.name + ": " +
              PresentationUtil.presentationString(b.getType(TypingContext.empty).getOrAny)
      case _ => element.name
    }
  }

  def getIconFlags: Int = 0

  def getContainerText(element: PsiNamedElement, name: String) = null //todo: add package name
}