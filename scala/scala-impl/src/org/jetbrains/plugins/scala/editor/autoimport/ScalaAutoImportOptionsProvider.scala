package org.jetbrains.plugins.scala.editor.autoimport

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.DataManager
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder._
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, invokeLater}
import org.jetbrains.plugins.scala.project.NonNullableValueBasedListRenderer
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.ui.KotlinDslWrappers._

import java.util
import javax.swing.JLabel
import javax.swing.event.HyperlinkEvent

final class ScalaAutoImportOptionsProvider extends UiDslUnnamedConfigurable.Simple() with AutoImportOptionsProvider {

  override def createContent(_panel: Panel): Unit = {
    val as = ScalaApplicationSettings.getInstance()

    _panel.group(ScalaBundle.message("options.scala.display.name"), true, (p: Panel) => {
      p.row(null: JLabel, (row: Row) => {
        row.label(JavaBundle.message("combobox.paste.insert.imports"))
        val renderer = new NonNullableValueBasedListRenderer[Int](
          {
            case CodeInsightSettings.YES => ApplicationBundle.message("combobox.insert.imports.all")
            case CodeInsightSettings.NO => ApplicationBundle.message("combobox.insert.imports.none")
            case CodeInsightSettings.ASK => ApplicationBundle.message("combobox.insert.imports.ask")
            case _ => ""
          },
          null
        )
        val model = new CollectionComboBoxModel(util.Arrays.asList(CodeInsightSettings.YES, CodeInsightSettings.NO, CodeInsightSettings.ASK))
        val insertImportsCell = row.comboBox[Int](model, renderer)
        ComboBoxKt.bindItem(insertImportsCell, mutableProperty(as.ADD_IMPORTS_ON_PASTE)(as.ADD_IMPORTS_ON_PASTE = _))
        KUnit
      })

      p.row(ScalaBundle.message("auto.import.show.import.popup.for"), (_: Row) => KUnit)
      p.indent((p: Panel) => {
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.classes"), as.SHOW_IMPORT_POPUP_CLASSES, as.SHOW_IMPORT_POPUP_CLASSES = _)
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.methods"), as.SHOW_IMPORT_POPUP_STATIC_METHODS, as.SHOW_IMPORT_POPUP_STATIC_METHODS = _)
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.conversions"), as.SHOW_IMPORT_POPUP_CONVERSIONS, as.SHOW_IMPORT_POPUP_CONVERSIONS = _)
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.implicits"), as.SHOW_IMPORT_POPUP_IMPLICITS, as.SHOW_IMPORT_POPUP_IMPLICITS = _)
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.extension.methods"), as.SHOW_IMPORT_POPUP_EXTENSION_METHODS, as.SHOW_IMPORT_POPUP_EXTENSION_METHODS = _)
        KUnit
      })

      p.row(ScalaBundle.message("auto.import.add.unambiguous.imports.on.the.fly.for"), (_: Row) => KUnit)
      p.indent((_: Panel) => {
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.classes"), as.ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY, as.ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY = _)
        p.checkBoxCell(ScalaBundle.message("auto.import.show.popup.methods"), as.ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS, as.ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS = _)
        KUnit
      })

      p.checkBoxCell(ScalaBundle.message("auto.import.optimize.imports.on.the.fly"), as.OPTIMIZE_IMPORTS_ON_THE_FLY, as.OPTIMIZE_IMPORTS_ON_THE_FLY = _)
      p.row(null: JLabel, (row: Row) => {
        row.comment(
          ScalaBundle.message("auto.import.find.more.configuration.options"),
          -1,
          (_: HyperlinkEvent) => {
            selectImportsTabInScalaCodeStyleSettingsConfigurable()
          }
        )
        KUnit
      })
      KUnit
    })
  }

  private def selectImportsTabInScalaCodeStyleSettingsConfigurable(): Unit = {
    DataManager.getInstance.getDataContextFromFocusAsync.`then`[Unit] { dataContext =>
      if (dataContext != null)
        invokeLater {
          for {
            settings <- Settings.KEY.getData(dataContext).toOption
            configurable <- settings.find("preferences.sourceCode.Scala").asOptionOf[CodeStyleConfigurableWrapper]
          } {
            settings.select(configurable).doWhenDone { () =>
              configurable.selectTab(ScalaBundle.message("imports.panel.title"))
            }
          }
        }
    }
  }
}
