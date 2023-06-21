package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.options.{BeanConfigurable, SearchableConfigurable}
import com.intellij.util.ui.{JBUI, UI}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.awt.GridLayout
import javax.swing._
import scala.annotation.nowarn

class ScalaEditorSmartKeysConfigurable extends BeanConfigurable[ScalaApplicationSettings](ScalaApplicationSettings.getInstance) with SearchableConfigurable {
  override def getId: String = "ScalaSmartKeys"

  //noinspection ScalaExtractStringToBundle
  override def getDisplayName: String = "Scala"

  override def getHelpTopic: String = null

  override def enableSearch(option: String): Runnable = null

  override def disposeUIResources(): Unit = super.disposeUIResources()

  init()

  def init(): Unit = {
    val settings: ScalaApplicationSettings = getInstance()
    checkBox(ScalaBundle.message("indent.pasted.lines.at.caret"), () => settings.INDENT_PASTED_LINES_AT_CARET, settings.INDENT_PASTED_LINES_AT_CARET = _)
    checkBox(ScalaBundle.message("insert.pair.multiline.quotes"), () => settings.INSERT_MULTILINE_QUOTES, settings.INSERT_MULTILINE_QUOTES = _)
    checkBox(ScalaBundle.message("upgrade.to.interpolated"), () => settings.UPGRADE_TO_INTERPOLATED, settings.UPGRADE_TO_INTERPOLATED = _)
    checkBox(ScalaBundle.message("wrap.single.expression.body"), () => settings.WRAP_SINGLE_EXPRESSION_BODY, settings.WRAP_SINGLE_EXPRESSION_BODY = _)
    checkBox(ScalaBundle.message("delete.closing.brace"), () => settings.DELETE_CLOSING_BRACE, settings.DELETE_CLOSING_BRACE = _)

    // these checkboxes need special wrappers since they have tooltips
    val addBracesCheckbox = new JCheckBox(ScalaBundle.message("insert.block.braces.automatically.based.on.indentation"))
    component(
      (UI.PanelFactory.panel(addBracesCheckbox).withTooltip(ScalaBundle.message("insert.block.braces.automatically.based.on.indentation.tooltip")).createPanel(): @nowarn("cat=deprecation")),
      () => settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY, settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = _: Boolean,
      () => addBracesCheckbox.isSelected, addBracesCheckbox.setSelected(_)
    )
    val removeBracesCheckbox = new JCheckBox(ScalaBundle.message("remove.block.braces.automatically.based.on.indentation"))
    component(
      (UI.PanelFactory.panel(removeBracesCheckbox).withTooltip(ScalaBundle.message("remove.block.braces.automatically.based.on.indentation.tooltip")).createPanel(): @nowarn("cat=deprecation")),
      () => settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY, settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = _: Boolean,
      () => removeBracesCheckbox.isSelected, removeBracesCheckbox.setSelected(_)
    )
  }

  override def createComponent: JComponent = {
    val result = super.createComponent
    assert(result != null, "ScalaEditorSmartKeysConfigurable panel was not created")

    // Group the auto-brace options by padding them to the right and adding a caption
    val isAutoBraceOptionTitle = Set(
      ScalaBundle.message("insert.block.braces.automatically.based.on.indentation"),
      ScalaBundle.message("remove.block.braces.automatically.based.on.indentation"),
    )

    for {
      // get the panel
      panel <- result.getComponent(0).asOptionOf[JComponent]

      // get the auto-brace options
      panelComponents = panel.getComponents
      autoBraceOptions = panelComponents.collect {
        case jpanel: JPanel if jpanel.getComponent(0).asOptionOf[JPanel].exists(
          checkboxPanel => checkboxPanel.getComponent(0).asOptionOf[JCheckBox].exists(
            checkbox => isAutoBraceOptionTitle(checkbox.getText)))
        => jpanel
      }

      if autoBraceOptions.nonEmpty
      gridLayout <- panel.getLayout.asOptionOf[GridLayout]
    } {
      // add a label above the auto-brace options
      gridLayout.setRows(gridLayout.getRows + 1)
      val groupCaptionPosition = panelComponents.indexOf(autoBraceOptions.head)
      panel.add(new JLabel(ScalaBundle.message("control.curly.braces.based.on.line.indents")), groupCaptionPosition)

      // add a left margin
      autoBraceOptions.foreach(_.setBorder(JBUI.Borders.emptyLeft(16)))
    }

    result
  }
}
