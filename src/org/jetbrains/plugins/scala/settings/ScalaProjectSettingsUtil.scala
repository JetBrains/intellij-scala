package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.ui.{InputValidator, Messages}
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.{AnActionButton, AnActionButtonRunnable, ListScrollingUtil, ToolbarDecorator}
import java.util
import javax.swing.{DefaultListModel, JComponent, JList, JPanel}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 25.05.12
 */

object ScalaProjectSettingsUtil {
  def isValidPackage(packageName: String): Boolean = {
    if (packageName.trim.startsWith(".") || packageName.trim.endsWith(".")) return false
    val parts = packageName.split(".")
    for (i <- 0 until parts.length) {
      if (!ScalaNamesUtil.isIdentifier(parts(i)) || parts(i).isEmpty) {
        if (i != parts.length - 1 || parts(i) != "_") return false
      }
    }
    true
  }

  def getPatternValidator: InputValidator = new InputValidator {
    def checkInput(inputString: String): Boolean = {
      checkInput(inputString, checkExcludes = true)
    }

    @tailrec
    private def checkInput(inputString: String, checkExcludes: Boolean): Boolean = {
      if (checkExcludes && inputString.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX))
        checkInput(inputString.substring(ScalaCodeStyleSettings.EXCLUDE_PREFIX.length), checkExcludes = false)
      else
        inputString.contains(".") && ScalaProjectSettingsUtil.isValidPackage(inputString)
    }

    def canClose(inputString: String): Boolean = {
      checkInput(inputString)
    }
  }

  def getPatternListPanel(parent: JComponent, patternJBList: JList[_], inputMessage: String, inputTitle: String): JPanel = {
    def addPattern(pattern: String, patternJBList: JList[_]) {
      if (pattern == null) return
      val listModel = patternJBList.getModel match {
        case null => return
        case default: DefaultListModel[Any] => default
        case _ => return
      }
      val index: Int = - util.Arrays.binarySearch(listModel.toArray, pattern) - 1
      if (index < 0) return
      listModel.add(index, pattern)
      patternJBList.setSelectedValue(pattern, true)
      ListScrollingUtil.ensureIndexIsVisible(patternJBList, index, 0)
      IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
    }

    ToolbarDecorator.createDecorator(patternJBList).setAddAction(new AnActionButtonRunnable {
      def run(button: AnActionButton) {
        val validator: InputValidator = ScalaProjectSettingsUtil.getPatternValidator
        val pattern: String = Messages.showInputDialog(parent, inputMessage, inputTitle, Messages.getWarningIcon, "", validator)
        addPattern(pattern, patternJBList)
      }
    }).disableUpDownActions.createPanel
  }
}
