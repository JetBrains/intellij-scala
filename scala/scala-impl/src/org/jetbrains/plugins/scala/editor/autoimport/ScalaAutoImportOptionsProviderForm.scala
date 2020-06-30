package org.jetbrains.plugins.scala.editor.autoimport

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import javax.swing.{JCheckBox, JComboBox, JLabel, JPanel}
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt

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

    panel.add(new JLabel(ScalaBundle.message("auto.import.add.unambiguous.imports.on.the.fly.for")), wrap)
    panel.add(addUnambiguousImportsOnCheckBox, indent.wrap())
    panel.add(addUnambiguousImportsMethodsCheckBox, indent.wrap())

    panel.add(optimizeImportsOnTheCheckBox)
    addMoreOptionsLink()
  }

  private def wrap = new CC().wrap()
  private def indent = new CC().gapBefore("indent")

  private def lastFocusedOrDefaultProject: Project =
    IdeFocusManager.getGlobalInstance.getLastFocusedFrame.toOption
      .flatMap(_.getProject.toOption)
      .filter(!_.isDisposed)
      .getOrElse(ProjectManager.getInstance().getDefaultProject)

  private def addMoreOptionsLink(): Unit = {
    val project = lastFocusedOrDefaultProject
    val moreOptionsLabel = new JLabel(ScalaBundle.message("auto.import.find.more.options.in"))
    val linkLabel = new LinkLabel[Unit](ScalaBundle.message("auto.import.code.style.link"), null)

    val smallFont = JBUI.Fonts.smallFont()
    moreOptionsLabel.setFont(smallFont)
    linkLabel.setFont(smallFont)

    linkLabel.setListener((_, _) => {
      //todo: can we open Imports tab in Code style settings?
      ShowSettingsUtilImplExt.showScalaCodeStyleSettingsDialog(project, null)
    }, null)

    panel.add(moreOptionsLabel, new CC().split(2).newline("0"))
    panel.add(linkLabel)
  }
}
