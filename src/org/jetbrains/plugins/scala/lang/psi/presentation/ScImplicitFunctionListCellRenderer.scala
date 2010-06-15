package org.jetbrains.plugins.scala.lang.psi.presentation

import com.intellij.ide.util.MethodCellRenderer
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil
import javax.swing.JList
import java.awt.{Container, Color}
import com.intellij.util.ui.UIUtil
import com.intellij.ui.LightColors

/**
 * Created by IntelliJ IDEA.
 * User: Alexander.Podkhalyuz
 * Date: 15.06.2010
 * Time: 15:03:33
 * To change this template use File | Settings | File Templates.
 */

class ScImplicitFunctionListCellRenderer(actual: ScFunction) extends MethodCellRenderer(true) {
  override def getListCellRendererComponent(list: JList, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean) = {
    val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    val bgColor =
      if (isSelected) UIUtil.getListSelectionBackground
      else if (value != actual) UIUtil.getListBackground
      else LightColors.SLIGHTLY_GREEN
    comp match {
      case container: Container => {
        for (comp <- container.getComponents) {
          comp.setBackground(bgColor)
        }
      }
      case _ =>
    }
    comp.setBackground(bgColor)
    comp
  }

  override def getElementText(element: PsiMethod) = {
    element match {
      case method: ScFunction => {
        method.getName + PresentationUtil.presentationString(method.paramClauses) + ": " +
                PresentationUtil.presentationString(method.returnType.
                        getOrElse(org.jetbrains.plugins.scala.lang.psi.types.Any))
      }
      case _ => super.getElementText(element)
    }
  }

}