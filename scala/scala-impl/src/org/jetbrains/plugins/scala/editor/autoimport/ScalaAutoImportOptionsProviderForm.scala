package org.jetbrains.plugins.scala.editor.autoimport

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.{HyperlinkLabel, IdeBorderFactory}
import com.intellij.util.ui.UIUtil
import javax.swing.{JCheckBox, JComboBox, JLabel, JPanel}
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, invokeLater}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaTabbedCodeStylePanel

class ScalaAutoImportOptionsProviderForm {
  private val INSERT_IMPORTS_ALWAYS = ApplicationBundle.message("combobox.insert.imports.all")
  private val INSERT_IMPORTS_ASK = ApplicationBundle.message("combobox.insert.imports.ask")
  private val INSERT_IMPORTS_NONE = ApplicationBundle.message("combobox.insert.imports.none")

  private val panel = new JPanel()
  private val importOnPasteComboBox: JComboBox[String] = {
    val cb = new ComboBox[String]()
    cb.addItem(INSERT_IMPORTS_ALWAYS)
    cb.addItem(INSERT_IMPORTS_ASK)
    cb.addItem(INSERT_IMPORTS_NONE)
    cb
  }

  private val addUnambiguousImportsOnCheckBox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.classes"))
  private val addUnambiguousImportsMethodsCheckBox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.methods"))
  private val optimizeImportsOnTheCheckBox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.optimize.imports.on.the.fly"))
  private val classesCheckBox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.classes"))
  private val methodsCheckbox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.methods"))
  private val conversionsCheckbox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.conversions"))
  private val implicitsCheckbox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.implicits"))
  private val extensionMethodsCheckbox: JCheckBox = new JCheckBox(ScalaBundle.message("auto.import.show.popup.extension.methods"))

  initLayout()

  def isAddUnambiguous: Boolean = addUnambiguousImportsOnCheckBox.isSelected

  def setAddUnambiguous(addUnambiguous: Boolean): Unit = {
    addUnambiguousImportsOnCheckBox.setSelected(addUnambiguous)
  }

  def isAddUnambiguousMethods: Boolean = addUnambiguousImportsMethodsCheckBox.isSelected

  def setAddUnambiguousMethods(addUnambiguous: Boolean): Unit = {
    addUnambiguousImportsMethodsCheckBox.setSelected(addUnambiguous)
  }

  def isShowPopupClasses: Boolean = classesCheckBox.isSelected

  def setShowPopupClasses(value: Boolean): Unit = {
    classesCheckBox.setSelected(value)
  }

  def isShowPopupMethods: Boolean = methodsCheckbox.isSelected

  def setShowPopupMethods(value: Boolean): Unit = {
    methodsCheckbox.setSelected(value)
  }

  def isShowPopupConversions: Boolean = conversionsCheckbox.isSelected

  def setShowPopupConversions(value: Boolean): Unit = {
    conversionsCheckbox.setSelected(value)
  }

  def isShowPopupImplicits: Boolean = implicitsCheckbox.isSelected

  def setShowPopupImplicits(value: Boolean): Unit = {
    implicitsCheckbox.setSelected(value)
  }

  def isShowPopupExtensionMethods: Boolean = extensionMethodsCheckbox.isSelected

  def setShowPopupExtensionMethods(value: Boolean): Unit = {
    extensionMethodsCheckbox.setSelected(value)
  }

  def isOptimizeImports: Boolean = optimizeImportsOnTheCheckBox.isSelected

  def setOptimizeImports(optimizeImports: Boolean): Unit = {
    optimizeImportsOnTheCheckBox.setSelected(optimizeImports)
  }

  def getImportOnPasteOption: Int = if (importOnPasteComboBox.getSelectedItem == INSERT_IMPORTS_ALWAYS) CodeInsightSettings.YES
  else if (importOnPasteComboBox.getSelectedItem == INSERT_IMPORTS_ASK) CodeInsightSettings.ASK
  else CodeInsightSettings.NO

  def setImportOnPasteOption(importOnPasteOption: Int): Unit = {
    importOnPasteOption match {
      case CodeInsightSettings.YES =>
        importOnPasteComboBox.setSelectedItem(INSERT_IMPORTS_ALWAYS)

      case CodeInsightSettings.ASK =>
        importOnPasteComboBox.setSelectedItem(INSERT_IMPORTS_ASK)

      case CodeInsightSettings.NO =>
        importOnPasteComboBox.setSelectedItem(INSERT_IMPORTS_NONE)

    }
  }

  def getComponent: JPanel = panel

  private def initLayout(): Unit = {
    panel.setLayout(new MigLayout())
    panel.setBorder(IdeBorderFactory.createTitledBorder(ScalaBundle.message("options.scala.display.name")))

    panel.add(new JLabel(ScalaBundle.message("auto.import.insert.imports.on.paste")))
    panel.add(importOnPasteComboBox, wrap)

    panel.add(new JLabel(ScalaBundle.message("auto.import.show.import.popup.for")), wrap)
    panel.add(classesCheckBox, indent.wrap())
    panel.add(methodsCheckbox, indent.wrap())
    panel.add(conversionsCheckbox, indent.wrap())
    panel.add(implicitsCheckbox, indent.wrap())
    panel.add(extensionMethodsCheckbox, indent.wrap())

    panel.add(new JLabel(ScalaBundle.message("auto.import.add.unambiguous.imports.on.the.fly.for")), wrap)
    panel.add(addUnambiguousImportsOnCheckBox, indent.wrap())
    panel.add(addUnambiguousImportsMethodsCheckBox, indent.wrap())

    panel.add(optimizeImportsOnTheCheckBox)
    addMoreOptionsLink()
  }

  private def wrap = new CC().wrap()
  private def indent = new CC().gapBefore("indent")

  private def addMoreOptionsLink(): Unit = {
    val moreOptionsComponent = createFindMoreLinkComponent()
    panel.add(moreOptionsComponent, new CC().newline("0"))
  }

  private def createFindMoreLinkComponent() = {
    val label = new HyperlinkLabel()

    //noinspection ScalaExtractStringToBundle,ReferencePassedToNls
    label.setTextWithHyperlink(
      ScalaBundle.message("auto.import.find.more.options.in") + "<hyperlink>" +
      ScalaBundle.message("auto.import.code.style.link")      + "</hyperlink>"
    )
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, label)

    label.addHyperlinkListener { _ =>
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
    label
  }
}
