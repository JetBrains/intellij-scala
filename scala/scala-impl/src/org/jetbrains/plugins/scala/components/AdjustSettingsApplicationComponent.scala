package org.jetbrains.plugins.scala
package components

import java.awt.{BorderLayout, GridLayout}
import java.util.regex.Pattern
import javax.swing._

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * @author Alefas
 * @since 11/09/14.
 */
class AdjustSettingsApplicationComponent extends ApplicationComponent {
  private val MEM_SIZE_EXPR: String = "(\\d*)([a-zA-Z]*)"
  private val XSS_PATTERN: Pattern = Pattern.compile("-Xss" + MEM_SIZE_EXPR)
  private val XMS_PATTERN: Pattern = Pattern.compile("-Xms" + MEM_SIZE_EXPR)

  override def initComponent(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) return
    if (ScalaApplicationSettings.getInstance().IGNORE_SETTINGS_CHECK) return

    val xmx = VMOptions.readXmx()

    if (xmx != -1) {
      val preferredXmx = (if (SystemInfo.is32Bit) 1024 else 2048).max(xmx)
      val xss = VMOptions.readOption(XSS_PATTERN).max(1)
      val preferredXss = (if (SystemInfo.is32Bit) 1 else 2).max(xss)
      val xms = VMOptions.readOption(XMS_PATTERN).max(0)
      val preferredXms = preferredXmx

      if (xmx < preferredXmx || xss < preferredXss || xms < preferredXms) {
        new AdjustSettingsDialog(preferredXmx, preferredXms, preferredXss).show()
      }
    }
  }

  override def disposeComponent(): Unit = {}

  override def getComponentName: String = "AdjustSettingsApplicationComponent"

  private class AdjustSettingsDialog(preferredXmx: Int, preferredXms: Int,
                                     preferredXss: Int) extends DialogWrapper(null, true, true) {
    var xmxField: JTextField = null
    var xmsField: JTextField = null
    var xssField: JTextField = null

    setTitle(IdeBundle.message("title.warning"))
    setButtonsAlignment(SwingConstants.CENTER)
    setCancelButtonText("Do not show again")
    setOKButtonText("Adjust and restart")
    init()

    def createCenterPanel: JComponent = null

    override def createNorthPanel: JComponent = {
      val panel = new JPanel(new BorderLayout)
      panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
      val icon = Messages.getWarningIcon
      if (icon != null) {
        val iconLabel = new JLabel(Messages.getQuestionIcon)
        panel.add(iconLabel, BorderLayout.WEST)
      }
      val adjustingPanel = new JPanel(new BorderLayout)
      panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
      val headerPanel = new JPanel(new GridLayout(0, 1, 0, 0))
      headerPanel.add(new JLabel("Recommended memory settings for Scala plugin:"))
      adjustingPanel.add(headerPanel, BorderLayout.NORTH)
      val settingsPanel = new JPanel(new GridLayout(0, 2, 0, 0))
      settingsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10))
      settingsPanel.add(new JLabel("-Xms (mb)"))
      xmsField = new JTextField(preferredXms.toString)
      settingsPanel.add(xmsField)

      settingsPanel.add(new JLabel("-Xmx (mb)"))
      xmxField = new JTextField(preferredXmx.toString)
      settingsPanel.add(xmxField)

      settingsPanel.add(new JLabel("-Xss (mb)"))
      xssField = new JTextField(preferredXss.toString)
      settingsPanel.add(xssField)

      adjustingPanel.add(settingsPanel, BorderLayout.SOUTH)
      panel.add(adjustingPanel, BorderLayout.CENTER)
      panel
    }

    override def doOKAction(): Unit = {
      try {
        val xmsValue = xmsField.getText.toInt
        val xmxValue = xmxField.getText.toInt
        val xssValue = xssField.getText.toInt
        if (!VMOptions.writeOption("-Xms", xmsValue, XMS_PATTERN)) {
          ScalaApplicationSettings.getInstance().IGNORE_SETTINGS_CHECK = true
          Messages.showErrorDialog("Settings weren't adjusted. Need permissions.", "Access is denied")
          return
        }
        VMOptions.writeXmx(xmxValue)
        VMOptions.writeOption("-Xss", xssValue, XSS_PATTERN)
      } catch {
        case _: NumberFormatException => //do nothing
      } finally {
        invokeLater(ApplicationManager.getApplication.restart())
        super.doOKAction() //close OK dialog
      }
    }

    override def doCancelAction(): Unit = {
      ScalaApplicationSettings.getInstance().IGNORE_SETTINGS_CHECK = true
      super.doCancelAction()
    }
  }
}
