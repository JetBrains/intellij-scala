package org.jetbrains.sbt.project.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.{ActionLink, JBLabel}
import com.intellij.ui.{JBColor, TitledSeparator}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.ui.{JBUI, UI, UIUtil}
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.sbt.{SbtBundle, SbtUtil}
import org.jetbrains.sbt.project.settings.SbtExtraControls.JCheckBoxPanel

import java.awt._
import java.awt.event.{ActionEvent, ActionListener}
import javax.swing._
import scala.annotation.{nowarn, unused}

final class SbtExtraControls {
  private val content: JComponent = new JPanel

  def rootComponent: JComponent = content

  var converterVersion = 0
  val resolveClassifiersCheckBox: JCheckBoxPanel = ct(boxLabel = SbtBundle.message("sbt.settings.resolveClassifiers"), tooltip = SbtBundle.message("sbt.settings.resolveClassifiers.tooltip"))
  val resolveSbtClassifiersCheckBox: JCheckBoxPanel = ct(boxLabel =SbtBundle.message("sbt.settings.resolveSbtClassifiers"), tooltip =SbtBundle.message("sbt.settings.resolveSbtClassifiers.tooltip"))
  val useSbtShellForImportCheckBox: JCheckBoxPanel = ct(boxLabel = SbtBundle.message("sbt.settings.useShellForImport"), tooltip = SbtBundle.message("sbt.settings.useShellForImport.tooltip"))
  val useSbtShellForBuildCheckBox: JCheckBoxPanel = ct(boxLabel = SbtBundle.message("sbt.settings.useShellForBuild"), tooltip = SbtBundle.message("sbt.settings.useShellForBuild.tooltip"))
  val remoteDebugSbtShellCheckBox: JCheckBoxPanel = ct(boxLabel = SbtBundle.message("sbt.settings.remoteDebug"), tooltip = SbtBundle.message("sbt.settings.remoteDebug.tooltip"))
  val scalaVersionPreferenceCheckBox: JCheckBoxPanel = ct(boxLabel = SbtBundle.message("sbt.settings.scalaVersionPreference"), tooltip = SbtBundle.message("sbt.settings.scalaVersionPreference.tooltip"))
  private val readMoreLink = new ActionLink(
    SbtBundle.message("separate.prod.test.modules.link.text"),
    (_ => SbtUtil.openSeparateMainTestModulesBlogPost()): ActionListener
  )
  val separateProdTestModules: JCheckBoxPanel = ct(
    boxLabel = SbtBundle.message("separate.prod.test.modules"),
    comment = SbtBundle.message("separate.prod.test.modules.comment"),
    extraComponents = Seq(readMoreLink)
  )
  val useSeparateCompilerOutputPaths: JCheckBoxPanel = ct(boxLabel = SbtBundle.message("use.separate.compiler.output.paths"), tooltip = SbtBundle.message("use.separate.compiler.output.paths.tooltip"))
  private val useSeparateCompilerOutputPathsWarning: JBLabel = new JBLabel(SbtBundle.message("use.separate.compiler.output.paths.warning"))

  val generateManagedSourcesDuringProjectSync: JCheckBoxPanel = ct(
    boxLabel = SbtBundle.message("generate.managed.sources.during.project.sync.label"),
    tooltip = SbtBundle.message("generate.managed.sources.during.project.sync.tooltip")
  )

  private def gc(row: Int, column: Int, rowSpan: Int, colSpan: Int) =
    new GridConstraints(row, column, rowSpan, colSpan, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false)

  locally {
    content.setLayout(new GridLayoutManager(12, 2, JBUI.emptyInsets(), -1, -1))

    val warningConstraints = new GridConstraints(
      6, 0, 1, 2,
      GridConstraints.ANCHOR_WEST,
      GridConstraints.FILL_NONE,
      GridConstraints.SIZEPOLICY_FIXED,
      GridConstraints.SIZEPOLICY_FIXED,
      null,
      null,
      new Dimension(500, -1),
      2,
      false
    )

    content.add(new JBLabel(SbtBundle.message("sbt.settings.download")), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(80, 16), null, 0, false))
    content.add(resolveClassifiersCheckBox.panel, gc(0, 1, 1, 1))
    content.add(resolveSbtClassifiersCheckBox.panel, gc(1, 1, 1, 1))
    content.add(scalaVersionPreferenceCheckBox.panel, gc(2, 0, 1, 2))
    content.add(useSeparateCompilerOutputPaths.panel, gc(3, 0, 1, 2))
    content.add(separateProdTestModules.panel, gc(4, 0, 1, 2))
    content.add(generateManagedSourcesDuringProjectSync.panel, gc(5, 0, 1, 2))
    content.add(useSeparateCompilerOutputPathsWarning, warningConstraints)
    content.add(new TitledSeparator(SbtBundle.message("sbt.settings.shell.title")), new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false))
    content.add(new JBLabel(SbtBundle.message("sbt.settings.useShell")), new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false))
    content.add(useSbtShellForImportCheckBox.panel, gc(8, 1, 1, 1))
    content.add(useSbtShellForBuildCheckBox.panel, gc(9, 1, 1, 1))
    content.add(remoteDebugSbtShellCheckBox.panel, gc(10, 0, 1, 2))
    content.add(new Spacer, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 5), null, new Dimension(-1, 1), 0, false))

    resolveClassifiersCheckBox.setEnabled(true)
    useSeparateCompilerOutputPathsWarning.setVisible(shouldWarningBeVisible)
    useSeparateCompilerOutputPathsWarning.setForeground(JBColor.RED)

    useSeparateCompilerOutputPaths.box.addActionListener(warningActionListener)
    useSbtShellForBuildCheckBox.box.addActionListener(warningActionListener)
  }

  private def withExtensions(
    component: JCheckBox,
    @Nls @Nullable tooltip: String,
    @Nls @Nullable comment: String,
    betaBadge: Boolean,
    extraComponents: Seq[JComponent]
  ): JPanel = {
    val panelBuilder = UI.PanelFactory.panel(component): @nowarn("cat=deprecation")
    val panelBuilderWithTooltip = if (tooltip != null) panelBuilder.withTooltip(tooltip) else panelBuilder
    val panel = panelBuilderWithTooltip.createPanel()

    panel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))

    def addToPanel(component: JComponent): Unit = {
      panel.add(Box.createHorizontalStrut(5))
      panel.add(component)
    }

    if (betaBadge) {
      addToPanel(new JBLabel(AllIcons.General.Beta))
    }
    extraComponents.foreach(addToPanel)

    if (comment != null) {
      panelWithComment(component, panel, comment)
    } else {
      panel
    }
  }

  private def panelWithComment(checkBox: JCheckBox, parentPanel: JPanel, @Nls text: String): JPanel = {
    val panel = new JPanel(new GridLayoutManager(2, 2, JBUI.emptyInsets(), 0, 0))
    panel.add(parentPanel, gc(0, 0, 1, 1))

    val comment = new JBLabel(text)
    comment.setFont(ComponentPanelBuilder.getCommentFont(comment.getFont)): @nowarn("cat=deprecation")
    comment.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    val leftOffset = UIUtil.getCheckBoxTextHorizontalOffset(checkBox)
    comment.setBorder(JBUI.Borders.emptyLeft(leftOffset))
    panel.add(comment, gc(1, 0, 1, 1))

    panel
  }

  private def ct(
    @Nls boxLabel: String,
    @Nls @Nullable tooltip: String = null,
    @Nls @Nullable comment: String = null,
    betaBadge: Boolean = false,
    extraComponents: Seq[JComponent] = Seq.empty
  ): JCheckBoxPanel = {
    val box = new JCheckBox(boxLabel)
    val panel = withExtensions(box, tooltip, comment, betaBadge, extraComponents)
    new JCheckBoxPanel(box, panel)
  }

  def refreshOutputPathsWarning(): Unit = {
    useSeparateCompilerOutputPathsWarning.setVisible(shouldWarningBeVisible)
  }

  def refreshCheckboxesConstraints(): Unit =
    refreshOutputPathsWarning()

  private def warningActionListener(@unused e: ActionEvent): Unit = {
    refreshOutputPathsWarning()
  }

  private def shouldWarningBeVisible: Boolean = {
    val checkedOutputPaths = useSeparateCompilerOutputPaths.isSelected
    val checkedUseShellForBuild = useSbtShellForBuildCheckBox.isSelected
    checkedOutputPaths && checkedUseShellForBuild
  }
}

object SbtExtraControls {
  final class JCheckBoxPanel(val box: JCheckBox, val panel: JPanel) {
    def isSelected: Boolean = box.isSelected
    def setSelected(value: Boolean): Unit = box.setSelected(value)
    def setEnabled(value: Boolean): Unit = box.setEnabled(value)
  }
}
