package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.options.{BoundConfigurable, SearchableConfigurable}
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.BuilderKt
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.util.ui.KotlinDslWrappers._

final class ScalaEditorSmartKeysConfigurable extends BoundConfigurable(
  //noinspection ScalaExtractStringToBundle
  "Scala",
  null
) with SearchableConfigurable {
  private val settings = ScalaApplicationSettings.getInstance

  override def getId: String = "ScalaSmartKeys"

  override def enableSearch(option: String): Runnable = null

  override def disposeUIResources(): Unit = super.disposeUIResources()

  override def createPanel(): DialogPanel = BuilderKt.panel { panel =>
    panel.checkBoxCell(ScalaBundle.message("indent.pasted.lines.at.caret"),
      settings.INDENT_PASTED_LINES_AT_CARET,
      settings.INDENT_PASTED_LINES_AT_CARET = _)
    panel.checkBoxCell(ScalaBundle.message("insert.pair.multiline.quotes"),
      settings.INSERT_MULTILINE_QUOTES,
      settings.INSERT_MULTILINE_QUOTES = _)
    panel.checkBoxCell(ScalaBundle.message("upgrade.to.interpolated"),
      settings.UPGRADE_TO_INTERPOLATED,
      settings.UPGRADE_TO_INTERPOLATED = _)
    panel.checkBoxCell(ScalaBundle.message("wrap.single.expression.body"),
      settings.WRAP_SINGLE_EXPRESSION_BODY,
      settings.WRAP_SINGLE_EXPRESSION_BODY = _)
    panel.checkBoxCell(ScalaBundle.message("delete.closing.brace"),
      settings.DELETE_CLOSING_BRACE,
      settings.DELETE_CLOSING_BRACE = _)

    panel.buttonsGroup(ScalaBundle.message("control.curly.braces.based.on.line.indents")) { groupPanel =>
      groupPanel.checkBoxCellWithTooltip(
        ScalaBundle.message("insert.block.braces.automatically.based.on.indentation"),
        ScalaBundle.message("insert.block.braces.automatically.based.on.indentation.tooltip"),
        settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY,
        settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = _)
      groupPanel.checkBoxCellWithTooltip(
        ScalaBundle.message("remove.block.braces.automatically.based.on.indentation"),
        ScalaBundle.message("remove.block.braces.automatically.based.on.indentation.tooltip"),
        settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY,
        settings.HANDLE_BLOCK_BRACES_REMOVAL_AUTOMATICALLY = _)
    }

    kotlin.Unit.INSTANCE
  }
}
