package org.jetbrains.plugins.scala.settings

import java.util
import javax.swing.{DefaultListModel, JComponent, JList, JPanel}

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.{InputValidator, Messages}
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.{AnActionButton, AnActionButtonRunnable, ListScrollingUtil, ToolbarDecorator}
import com.intellij.util.IconUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 25.05.12
 */

object ScalaProjectSettingsUtil {
  def isValidPackage(packageName: String, checkPlaceholder: Boolean = true): Boolean = {
    if (packageName.trim.startsWith(".") || packageName.trim.endsWith(".")) return false
    val parts = packageName.split(".")
    for (i <- 0 until parts.length) {
      if (!ScalaNamesUtil.isIdentifier(parts(i)) || parts(i).isEmpty) {
        if (!checkPlaceholder || i != parts.length - 1 || parts(i) != "_") return false
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

  def getPackageValidator: InputValidator = new InputValidator {
    def checkInput(inputString: String): Boolean = {
      ScalaProjectSettingsUtil.isValidPackage(inputString, checkPlaceholder = false)
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
        case default: DefaultListModel[Any @unchecked] => default
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

  def getUnsortedPatternListPanel(parent: JComponent, patternJBList: JList[_], inputMessage: String, inputTitle: String): JPanel = {
    def addPattern(pattern: String, patternJBList: JList[_]) {
      if (pattern == null) return
      val listModel = patternJBList.getModel match {
        case null => return
        case default: DefaultListModel[Any @unchecked] => default
        case _ => return
      }
      val index = patternJBList.getSelectedIndex
      listModel.add(index + 1, pattern)
      patternJBList.setSelectedValue(pattern, true)
      ListScrollingUtil.ensureIndexIsVisible(patternJBList, index, 0)
      IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
    }

    ToolbarDecorator.createDecorator(patternJBList).setAddAction(new AnActionButtonRunnable {
      def run(button: AnActionButton) {
        val validator: InputValidator = ScalaProjectSettingsUtil.getPackageValidator
        val pattern: String = Messages.showInputDialog(parent, inputMessage, inputTitle, Messages.getWarningIcon, "", validator)
        addPattern(pattern, patternJBList)
      }
    }).addExtraAction(new AnActionButton(ApplicationBundle.message("button.add.blank"), IconUtil.getAddBlankLineIcon) {
      def actionPerformed(e: AnActionEvent) {
        addPattern(ScalaCodeStyleSettings.BLANK_LINE, patternJBList)
      }
    }).setRemoveAction(new AnActionButtonRunnable {
      override def run(t: AnActionButton): Unit = {
        val listModel = patternJBList.getModel match {
          case null => return
          case default: DefaultListModel[Any @unchecked] => default
          case _ => return
        }
        val index = patternJBList.getSelectedIndex
        if (index != -1) {
          if (listModel.get(index) == ScalaCodeStyleSettings.ALL_OTHER_IMPORTS) return
          val size = listModel.size()
          listModel.remove(index)
          val to = if (index == size - 1) index - 1 else index
          patternJBList.setSelectedIndex(to)
          ListScrollingUtil.ensureIndexIsVisible(patternJBList, to, 0)
          IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
        }
      }
    }).createPanel
  }
}
