package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.options.{BaseConfigurable, SearchableConfigurable}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import javax.swing._
import javax.swing.border.EmptyBorder
import org.jetbrains.plugins.scala.ScalaBundle

class ScalaEditorSmartKeysConfigurable extends BaseConfigurable with SearchableConfigurable {
  private var myChbInsertMultilineQuotes: JCheckBox = _
  private var myChbUpgradeToInterpolated: JCheckBox = _

  override def getId: String = "ScalaSmartKeys"
  override def getDisplayName: String = "Scala"
  override def getHelpTopic: String = null
  override def enableSearch(option: String): Runnable = null

  override def createComponent: JComponent = {
    val panel = new JPanel(new GridLayoutManager(2, 1))
    panel.setBorder(BorderFactory.createTitledBorder(new EmptyBorder(0, 0, 0, 0), "Scala"))
    myChbInsertMultilineQuotes = new JCheckBox(ScalaBundle.message("insert.pair.multiline.quotes"))
    myChbUpgradeToInterpolated = new JCheckBox(ScalaBundle.message("upgrade.to.interpolated"))
    myChbInsertMultilineQuotes.addActionListener(_ => ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected)
    myChbUpgradeToInterpolated.addActionListener(_ => ScalaApplicationSettings.getInstance.UPGRADE_TO_INTERPOLATED = myChbUpgradeToInterpolated.isSelected)
    panel.add(myChbInsertMultilineQuotes, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    panel.add(myChbUpgradeToInterpolated, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    panel
  }

  override def apply(): Unit = {
    ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected
    ScalaApplicationSettings.getInstance.UPGRADE_TO_INTERPOLATED = myChbUpgradeToInterpolated.isSelected
  }

  override def reset(): Unit = {
    myChbInsertMultilineQuotes.setSelected(ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES)
    myChbUpgradeToInterpolated.setSelected(ScalaApplicationSettings.getInstance.UPGRADE_TO_INTERPOLATED)
  }

  override def disposeUIResources(): Unit = {
  }
}