package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import java.awt._
import java.awt.event.{ItemEvent, ItemListener}
import java.util
import javax.swing._

import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{ComboBox, ValidationInfo}
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.table.TableView
import com.intellij.ui.{CommonActionsPanel, EditorTextField}
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaAddParameterDialog(project: Project,
                              method: ScalaMethodDescriptor,
                              introduceData: ScalaIntroduceParameterData)
        extends ScalaChangeSignatureDialog(project, method) {

  private var paramNameField: EditorTextField = _
  private var typeCombobox: JComboBox = _
  private var typeMap: util.TreeMap[String, ScType] = _
  private var replaceOccurrencesChb: JCheckBox = _

  override def init(): Unit = {
    super.init()
    setTitle(ScalaIntroduceParameterHandler.REFACTORING_NAME)
    hideAddAndRemoveButtons()
  }

  override def createNorthPanel(): JComponent = {
    val panel = super.createNorthPanel() //to initialize fields
    val northPanel = new JPanel(new GridBagLayout())
    val gbc: GridBagConstraints = new GridBagConstraints(0, 0, 1, 1, 1, 1,
      GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0)

    val paramNamePanel = createParamNamePanel()
    val paramTypePanel = createParamTypePanel()

    northPanel.add(paramNamePanel, gbc)
    gbc.gridx += 1
    northPanel.add(paramTypePanel, gbc)

    northPanel.add(panel)
    panel.setVisible(false)

    val container = new JPanel(new BorderLayout())
    container.add(northPanel, BorderLayout.NORTH)
    container.add(createDefaultArgumentPanel(), BorderLayout.CENTER)
    container
  }

  override def isListTableViewSupported: Boolean = false

  override def createRefactoringProcessor(): BaseRefactoringProcessor = {
    val parameters = splittedItems.map(_.map(_.parameter))
    val changeInfo =
      new ScalaChangeInfo(getVisibility, method.fun, getMethodName, returnType, parameters, isAddDefaultArgs)

    val newData = introduceData.copy(paramName = paramNameField.getText, tp = typeMap.get(typeCombobox.getSelectedItem),
      replaceAll = replaceOccurrencesChb.isSelected)

    changeInfo.introducedParameterData = Some(newData)
    new ScalaChangeSignatureProcessor(project, changeInfo)
  }

  override def createOptionsPanel(): JComponent = {
    val panel = super.createOptionsPanel()
    replaceOccurrencesChb = new JCheckBox("Replace all occurrences")
    replaceOccurrencesChb.setMnemonic('a')
    replaceOccurrencesChb.setSelected(false)
    replaceOccurrencesChb.setVisible(introduceData.occurrences.length > 1)
    panel.add(replaceOccurrencesChb)
    panel
  }

  override def createParametersInfoModel(method: ScalaMethodDescriptor): ScalaParameterTableModel = {
    new ScalaIntroduceParameterTableModel(method.fun, method.fun, method)
  }

  override def customizeParametersTable(table: TableView[ScalaParameterTableModelItem]): Unit = {
    table.setCellSelectionEnabled(false)
    table.setRowSelectionAllowed(true)
    table.setSelection(util.Collections.emptyList())
  }

  override def getPreferredFocusedComponent: JComponent = paramNameField

  protected override def doValidate(): ValidationInfo = null

  private def createParamNamePanel(): JComponent = {
    paramNameField = new EditorTextField(introduceData.paramName)
    paramNameField.setPreferredWidth(150)
    paramNameField.addDocumentListener(new DocumentAdapter {
      override def documentChanged(e: DocumentEvent): Unit = {
        val newText: String = paramNameField.getText
        introducedParamTableItem.foreach(_.parameter.setName(newText))
        myParametersTableModel.fireTableDataChanged()
        updateSignatureAlarmFired()
      }
    })
    val paramNameLabel = new JLabel("Name:")
    paramNameLabel.setDisplayedMnemonic('N')
    paramNameLabel.setLabelFor(paramNameField)
    val paramNamePanel = new JPanel(new BorderLayout(0, 2))
    paramNamePanel.add(paramNameLabel, BorderLayout.NORTH)
    IJSwingUtilities.adjustComponentsOnMac(paramNameLabel, paramNameField)
    paramNamePanel.add(paramNameField, BorderLayout.SOUTH)
    paramNamePanel
  }

  private def createParamTypePanel(): JComponent = {
    typeCombobox = new ComboBox()
    val typeLabel = new JLabel("Type:")
    typeLabel.setLabelFor(typeCombobox)
    typeMap = ScalaRefactoringUtil.getCompatibleTypeNames(introduceData.possibleTypes)
    for (typeName <- typeMap.keySet.asScala) {
      typeCombobox.addItem(typeName)
    }
    typeLabel.setDisplayedMnemonic('T')
    typeCombobox.addItemListener(new ItemListener {
      override def itemStateChanged(e: ItemEvent): Unit = {
        val scType = typeMap.get(typeCombobox.getSelectedItem)
        introducedParamTableItem.foreach(_.parameter.scType = scType)
        myParametersTableModel.fireTableDataChanged()
        updateSignatureAlarmFired()
      }
    })
    val paramTypePanel = new JPanel(new BorderLayout(0, 2))
    paramTypePanel.add(typeLabel, BorderLayout.NORTH)
    IJSwingUtilities.adjustComponentsOnMac(typeLabel, typeCombobox)
    paramTypePanel.add(typeCombobox, BorderLayout.SOUTH)

    paramTypePanel
  }

  private def createDefaultArgumentPanel(): JComponent = {
    val panel = new JPanel(new BorderLayout())
    val textField = new EditorTextField(introduceData.defaultArg, project, ScalaFileType.SCALA_FILE_TYPE)
    val label = new JLabel("Default value:")
    label.setLabelFor(textField)
    panel.add(label, BorderLayout.NORTH)
    textField.setOneLineMode(false)
    textField.setEnabled(false)
    IJSwingUtilities.adjustComponentsOnMac(label, textField)
    panel.add(textField, BorderLayout.SOUTH)
    panel
  }

  private def hideAddAndRemoveButtons(): Unit = {
    val actionsPanel = UIUtil.findComponentOfType(getContentPanel, classOf[CommonActionsPanel]).toOption
    actionsPanel.foreach { p =>
      p.getAnActionButton(CommonActionsPanel.Buttons.ADD).setVisible(false)
      p.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).setVisible(false)
    }
  }

  private def introducedParamTableItem: Option[ScalaParameterTableModelItem] = {
    parameterItems.find(_.parameter.isIntroducedParameter)
  }

}
