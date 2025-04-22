package org.jetbrains.plugins.scala.project.template

import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.{AlignX, TextFieldKt}
import com.intellij.util.ui.UI
import org.jetbrains.plugins.scala.ScalaBundle

import javax.swing.{JLabel, JPanel}
import scala.annotation.nowarn

trait PackagePrefixStepLike {

  private val packagePrefixHelpText: String = ScalaBundle.message("package.prefix.help")

  /**
   * In NewProjectWizard we can't use `prefixPanel` created with  `UI.PanelFactory.panel.withTooltip`
   * because it adds some strange indent to the left of the panel, which looks ugly.<br>
   * I didn't find a nice way to fix this, so we set tooltip on a fielf when using NPW
   */
  protected val packagePrefixTextField: JBTextField = {
    val tf = new JBTextField()
    tf.getEmptyText.setText(ScalaBundle.message("package.prefix.example"))
    tf.setToolTipText(packagePrefixHelpText)
    tf.setColumns(TextFieldKt.COLUMNS_LARGE)
    tf
  }

  protected val packagePrefixPanelWithTooltip: JPanel = UI.PanelFactory
    .panel(packagePrefixTextField)
    .withTooltip(packagePrefixHelpText)
    .createPanel(): @nowarn("cat=deprecation")

  protected val packagePrefixLabelText: String = ScalaBundle.message("package.prefix.label")

  private val packagePrefixLabel: JLabel =
    new JLabel(packagePrefixLabelText)

  protected def setupPackagePrefixUI(panel: com.intellij.ui.dsl.builder.Panel): Unit = {
    panel.row(packagePrefixLabel, (row: com.intellij.ui.dsl.builder.Row) => {
      row.cell(packagePrefixTextField).align(AlignX.LEFT.INSTANCE)
      kotlin.Unit.INSTANCE
    })
  }
}
