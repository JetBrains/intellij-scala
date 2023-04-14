package org.jetbrains.sbt.project.settings

import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.ui.{JBUI, UI}
import org.jetbrains.annotations.Nls
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.settings.SbtExtraControls.JCheckBoxWithTooltip

import java.awt._
import javax.swing._

final class SbtExtraControls {
  private val content: JComponent = new JPanel

  def rootComponent: JComponent = content

  var converterVersion = 0
  val resolveClassifiersCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.resolveClassifiers"), SbtBundle.message("sbt.settings.resolveClassifiers.tooltip"))
  val resolveSbtClassifiersCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.resolveSbtClassifiers"), SbtBundle.message("sbt.settings.resolveSbtClassifiers.tooltip"))
  val useSbtShellForImportCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.useShellForImport"), SbtBundle.message("sbt.settings.useShellForImport.tooltip"))
  val useSbtShellForBuildCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.useShellForBuild"), SbtBundle.message("sbt.settings.useShellForBuild.tooltip"))
  val remoteDebugSbtShellCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.remoteDebug"), SbtBundle.message("sbt.settings.remoteDebug.tooltip"))
  val scalaVersionPreferenceComboBox = new com.intellij.openapi.ui.ComboBox(Array("Scala 2", "Scala 3"))

  locally {
    content.setLayout(new GridLayoutManager(9, 2, JBUI.emptyInsets(), -1, -1))

    def gc(row: Int, column: Int, rowSpan: Int, colSpan: Int) =
      new GridConstraints(row, column, rowSpan, colSpan, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false)

    val scalaVersionPreferencePanel = {
      val panel = new JPanel()
      panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS))
      panel.add(new JBLabel(SbtBundle.message("sbt.settings.scalaVersionPreference")))
      panel.add(Box.createRigidArea(new Dimension(10, 0)))
      panel.add(withTooltip(scalaVersionPreferenceComboBox, SbtBundle.message("sbt.settings.scalaVersionPreference.tooltip")))
      panel
    }

    content.add(new JBLabel(SbtBundle.message("sbt.settings.download")), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(80, 16), null, 0, false))
    content.add(resolveClassifiersCheckBox.panelWithTooltip, gc(0, 1, 1, 1))
    content.add(resolveSbtClassifiersCheckBox.panelWithTooltip, gc(1, 1, 1, 1))
    content.add(scalaVersionPreferencePanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false))
    content.add(new TitledSeparator(SbtBundle.message("sbt.settings.shell.title")), new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false))
    content.add(new JBLabel(SbtBundle.message("sbt.settings.useShell")), new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    content.add(useSbtShellForImportCheckBox.panelWithTooltip, gc(4, 1, 1, 1))
    content.add(useSbtShellForBuildCheckBox.panelWithTooltip, gc(5, 1, 1, 1))
    content.add(new Spacer, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 5), null, new Dimension(-1, 1), 0, false))
    content.add(remoteDebugSbtShellCheckBox.panelWithTooltip, gc(7, 0, 1, 2))

    resolveClassifiersCheckBox.setEnabled(true)
  }

  private def withTooltip(component: JComponent, @Nls tooltip: String): JPanel =
    UI.PanelFactory.panel(component).withTooltip(tooltip).createPanel()

  private def ct(@Nls boxLabel: String, @Nls tooltip: String): JCheckBoxWithTooltip = {
    val box = new JCheckBox(boxLabel)
    val tooltipPanel = withTooltip(box, tooltip)
    new JCheckBoxWithTooltip(box, tooltipPanel)
  }
}

object SbtExtraControls {
  final class JCheckBoxWithTooltip(val box: JCheckBox, val panelWithTooltip: JPanel) {
    def isSelected: Boolean = box.isSelected
    def setSelected(value: Boolean): Unit = box.setSelected(value)
    def setEnabled(value: Boolean): Unit = box.setEnabled(value)
  }
}
