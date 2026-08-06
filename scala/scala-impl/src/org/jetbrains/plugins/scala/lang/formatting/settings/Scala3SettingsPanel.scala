package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.{JBCheckBox, JBPanel}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.ScalaBundle

import javax.swing.JPanel

class Scala3SettingsPanel(settings: CodeStyleSettings) extends ScalaCodeStyleSubPanelBase(settings) {

  private var innerPanel: JPanel = _
  private var checkbox: JBCheckBox = _

  private def buildInnerPanel(): JPanel = {
    val panel = new JBPanel
    panel.setLayout(new GridLayoutManager(1, 1, JBUI.emptyInsets(), -1, -1))
    panel.setBorder(IdeBorderFactory.createTitledBorder(ScalaBundle.message("scala3.panel.title")))

    checkbox = new JBCheckBox(ScalaBundle.message("scala3.panel.use.indentation.based.syntax"))

    import com.intellij.uiDesigner.core.GridConstraints._
    panel.add(checkbox, new GridConstraints(0, 0, 1, 1, ANCHOR_WEST, FILL_NONE, SIZEPOLICY_FIXED, SIZEPOLICY_WANT_GROW, null, null, null, 0, false))
    panel
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    val ss = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    ss.USE_SCALA3_INDENTATION_BASED_SYNTAX = checkbox.isSelected
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val ss = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    checkbox.isSelected != ss.USE_SCALA3_INDENTATION_BASED_SYNTAX
  }

  override def getPanel: JPanel = {
    if (innerPanel == null) {
      innerPanel = buildInnerPanel()
    }
    innerPanel
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    val ss = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    checkbox.setSelected(ss.USE_SCALA3_INDENTATION_BASED_SYNTAX)
  }
}
