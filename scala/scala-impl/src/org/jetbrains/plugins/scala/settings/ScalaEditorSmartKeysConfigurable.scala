package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.options.{BaseConfigurable, SearchableConfigurable}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import javax.swing._
import javax.swing.border.EmptyBorder
import org.jetbrains.plugins.scala.ScalaBundle

class ScalaEditorSmartKeysConfigurable extends BaseConfigurable with SearchableConfigurable {
  private var myChbInsertMultilineQuotes: JCheckBox = _
  private var myChbRemoveMultilineQuotes: JCheckBox = _
  private var myChbUpgradeToInterpolated: JCheckBox = _

  override def getId: String = "ScalaSmartKeys"
  override def getDisplayName: String = "Scala"
  override def getHelpTopic: String = null
  override def enableSearch(option: String): Runnable = null

  override def createComponent: JComponent = {
    myChbInsertMultilineQuotes = new JCheckBox(ScalaBundle.message("insert.pair.multiline.quotes"))
    myChbRemoveMultilineQuotes = new JCheckBox(ScalaBundle.message("remove.pair.multiline.quotes"))
    myChbUpgradeToInterpolated = new JCheckBox(ScalaBundle.message("upgrade.to.interpolated"))

    val allCheckBoxes: Seq[JCheckBox] = Seq(
      myChbInsertMultilineQuotes,
      myChbRemoveMultilineQuotes,
      myChbUpgradeToInterpolated
    )

    val totalRows: Int = allCheckBoxes.size
    val panel = new JPanel(new GridLayoutManager(totalRows, 1))
    panel.setBorder(BorderFactory.createTitledBorder(new EmptyBorder(0, 0, 0, 0), "Scala"))

    myChbInsertMultilineQuotes.addActionListener(_ => settings.INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected)
    myChbRemoveMultilineQuotes.addActionListener(_ => settings.REMOVE_MULTILINE_QUOTES = myChbRemoveMultilineQuotes.isSelected)
    myChbUpgradeToInterpolated.addActionListener(_ => settings.UPGRADE_TO_INTERPOLATED = myChbUpgradeToInterpolated.isSelected)

    allCheckBoxes.zipWithIndex.foreach { case (checkBox, idx) =>
      panel.add(checkBox, new GridConstraints(idx, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    }

    panel
  }

  override def apply(): Unit = {
    settings.INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected
    settings.REMOVE_MULTILINE_QUOTES = myChbRemoveMultilineQuotes.isSelected
    settings.UPGRADE_TO_INTERPOLATED = myChbUpgradeToInterpolated.isSelected
  }

  override def reset(): Unit = {
    myChbInsertMultilineQuotes.setSelected(settings.INSERT_MULTILINE_QUOTES)
    myChbRemoveMultilineQuotes.setSelected(settings.REMOVE_MULTILINE_QUOTES)
    myChbUpgradeToInterpolated.setSelected(settings.UPGRADE_TO_INTERPOLATED)
  }

  override def disposeUIResources(): Unit = ()

  private def settings: ScalaApplicationSettings = ScalaApplicationSettings.getInstance
}