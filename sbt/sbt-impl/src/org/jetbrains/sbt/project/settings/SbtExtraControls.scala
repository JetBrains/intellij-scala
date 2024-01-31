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
import scala.annotation.nowarn

final class SbtExtraControls {
  private val content: JComponent = new JPanel

  def rootComponent: JComponent = content

  var converterVersion = 0
  val resolveClassifiersCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.resolveClassifiers"), SbtBundle.message("sbt.settings.resolveClassifiers.tooltip"))
  val resolveSbtClassifiersCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.resolveSbtClassifiers"), SbtBundle.message("sbt.settings.resolveSbtClassifiers.tooltip"))
  val useSbtShellForImportCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.useShellForImport"), SbtBundle.message("sbt.settings.useShellForImport.tooltip"))
  val useSbtShellForBuildCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.useShellForBuild"), SbtBundle.message("sbt.settings.useShellForBuild.tooltip"))
  val remoteDebugSbtShellCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.remoteDebug"), SbtBundle.message("sbt.settings.remoteDebug.tooltip"))
  val scalaVersionPreferenceCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("sbt.settings.scalaVersionPreference"), SbtBundle.message("sbt.settings.scalaVersionPreference.tooltip"))
  val groupProjectsFromSameBuildCheckBox: JCheckBoxWithTooltip = ct(SbtBundle.message("group.projects.from.the.same.build"), SbtBundle.message("group.projects.from.the.same.build.tooltip"))
  val insertProjectTransitiveDependencies: JCheckBoxWithTooltip = ct(SbtBundle.message("insert.project.transitive.dependencies"), SbtBundle.message("insert.project.transitive.dependencies.tooltip"))
  val useSeparateCompilerOutputPaths: JCheckBoxWithTooltip = ct(SbtBundle.message("use.separate.compiler.output.paths"), SbtBundle.message("use.separate.compiler.output.paths.tooltip"))


  locally {
    content.setLayout(new GridLayoutManager(11, 2, JBUI.emptyInsets(), -1, -1))

    def gc(row: Int, column: Int, rowSpan: Int, colSpan: Int) =
      new GridConstraints(row, column, rowSpan, colSpan, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false)

    content.add(new JBLabel(SbtBundle.message("sbt.settings.download")), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(80, 16), null, 0, false))
    content.add(resolveClassifiersCheckBox.panelWithTooltip, gc(0, 1, 1, 1))
    content.add(resolveSbtClassifiersCheckBox.panelWithTooltip, gc(1, 1, 1, 1))
    content.add(scalaVersionPreferenceCheckBox.panelWithTooltip, gc(2, 0, 1, 2))
    content.add(groupProjectsFromSameBuildCheckBox.panelWithTooltip, gc(3, 0, 1, 2))
    content.add(insertProjectTransitiveDependencies.panelWithTooltip, gc(4, 0, 1, 2))
    content.add(useSeparateCompilerOutputPaths.panelWithTooltip, gc(5, 0, 1, 2))
    content.add(new TitledSeparator(SbtBundle.message("sbt.settings.shell.title")), new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false))
    content.add(new JBLabel(SbtBundle.message("sbt.settings.useShell")), new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    content.add(useSbtShellForImportCheckBox.panelWithTooltip, gc(7, 1, 1, 1))
    content.add(useSbtShellForBuildCheckBox.panelWithTooltip, gc(8, 1, 1, 1))
    content.add(remoteDebugSbtShellCheckBox.panelWithTooltip, gc(9, 0, 1, 2))
    content.add(new Spacer, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 5), null, new Dimension(-1, 1), 0, false))

    resolveClassifiersCheckBox.setEnabled(true)
  }

  private def withTooltip(component: JComponent, @Nls tooltip: String): JPanel =
    UI.PanelFactory.panel(component).withTooltip(tooltip).createPanel(): @nowarn("cat=deprecation")

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
