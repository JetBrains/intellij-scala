package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.{InputValidator, Messages}
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui._
import com.intellij.ui.components.JBList
import com.intellij.util.IconUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier

import java.util
import javax.swing.{DefaultListModel, JComponent, JPanel}
import scala.annotation.tailrec

object ScalaProjectSettingsUtil {
  def isValidPackage(packageName: String, checkPlaceholder: Boolean = true): Boolean = {
    if (packageName.trim.startsWith(".") || packageName.trim.endsWith(".")) return false
    val parts = packageName.split(".")
    for (i <- 0 until parts.length) {
      val part = parts(i)
      if (!isIdentifier(part)) {
        if (!checkPlaceholder || i != parts.length - 1 || part != "_") return false
      }
    }
    true
  }

  def getPatternValidator: InputValidator = new InputValidator {
    override def checkInput(inputString: String): Boolean = {
      checkInput(inputString, checkExcludes = true)
    }

    @tailrec
    private def checkInput(inputString: String, checkExcludes: Boolean): Boolean = {
      if (checkExcludes && inputString.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX))
        checkInput(inputString.substring(ScalaCodeStyleSettings.EXCLUDE_PREFIX.length), checkExcludes = false)
      else
        inputString.contains(".") && ScalaProjectSettingsUtil.isValidPackage(inputString)
    }

    override def canClose(inputString: String): Boolean = {
      checkInput(inputString)
    }
  }

  def getPackageValidator: InputValidator = new InputValidator {
    override def checkInput(inputString: String): Boolean = {
      ScalaProjectSettingsUtil.isValidPackage(inputString, checkPlaceholder = false)
    }

    override def canClose(inputString: String): Boolean = {
      checkInput(inputString)
    }
  }

  def getPatternListPanel(parent: JComponent, patternJBList: JBList[String], @Nls inputMessage: String, @Nls inputTitle: String): JPanel = {
    def addPattern(pattern: String, patternJBList: JBList[String]): Unit = {
      if (pattern == null) return
      val listModel = patternJBList.getModel match {
        case default: DefaultListModel[String] => default
        case _ => return
      }
      val index: Int = - util.Arrays.binarySearch(listModel.toArray, pattern) - 1
      if (index < 0) return
      listModel.add(index, pattern)
      patternJBList.setSelectedValue(pattern, true)
      ScrollingUtil.ensureIndexIsVisible(patternJBList, index, 0)
      IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
    }

    ToolbarDecorator.createDecorator(patternJBList).setAddAction((_: AnActionButton) => {
      val validator: InputValidator = ScalaProjectSettingsUtil.getPatternValidator
      val pattern: String = Messages.showInputDialog(parent, inputMessage, inputTitle, Messages.getWarningIcon, "", validator)
      addPattern(pattern, patternJBList)
    }).disableUpDownActions.createPanel
  }

  def getUnsortedPatternListPanel(parent: JComponent, patternJBList: JBList[String], @Nls inputMessage: String, @Nls inputTitle: String): JPanel = {
    def addPattern(pattern: String, patternJBList: JBList[String]): Unit = {
      if (pattern == null) return
      val listModel = patternJBList.getModel match {
        case default: DefaultListModel[String] => default
        case _ => return
      }
      val index = patternJBList.getSelectedIndex
      listModel.add(index + 1, pattern)
      patternJBList.setSelectedValue(pattern, true)
      ScrollingUtil.ensureIndexIsVisible(patternJBList, index, 0)
      IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
    }

    ToolbarDecorator.createDecorator(patternJBList).setAddAction((_: AnActionButton) => {
      val validator: InputValidator = ScalaProjectSettingsUtil.getPackageValidator
      val pattern: String = Messages.showInputDialog(parent, inputMessage, inputTitle, Messages.getWarningIcon, "", validator)
      addPattern(pattern, patternJBList)
    }).addExtraAction(new AnActionButton(ApplicationBundle.message("button.add.blank"), IconUtil.getAddBlankLineIcon) {
      override def actionPerformed(e: AnActionEvent): Unit = {
        addPattern(ScalaCodeStyleSettings.BLANK_LINE, patternJBList)
      }
    }).setRemoveAction(new AnActionButtonRunnable {
      override def run(t: AnActionButton): Unit = {
        val listModel = patternJBList.getModel match {
          case default: DefaultListModel[String] => default
          case _ => return
        }
        val index = patternJBList.getSelectedIndex
        if (index != -1) {
          if (listModel.get(index) == ScalaCodeStyleSettings.ALL_OTHER_IMPORTS) return
          if (listModel.get(index) == ScalaCodeStyleSettings.BASE_PACKAGE_IMPORTS) return
          val size = listModel.size()
          listModel.remove(index)
          val to = if (index == size - 1) index - 1 else index
          patternJBList.setSelectedIndex(to)
          ScrollingUtil.ensureIndexIsVisible(patternJBList, to, 0)
          IdeFocusManager.getGlobalInstance.requestFocus(patternJBList, false)
        }
      }
    }).createPanel
  }
}
