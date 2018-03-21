package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import java.awt._
import java.util

import javax.swing._
import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent, DocumentListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{ComboBox, ValidationInfo}
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.table.{JBTable, TableView}
import com.intellij.ui.{EditorTextField, ToolbarDecorator}
import com.intellij.util.IJSwingUtilities
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.JListCompatibility
import org.jetbrains.plugins.scala.lang.refactoring._

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaIntroduceParameterDialog(project: Project,
                              method: ScalaMethodDescriptor,
                              introduceData: ScalaIntroduceParameterData)
        extends ScalaChangeSignatureDialog(project, method, false) {

  private var paramNameField: EditorTextField = _
  private var typeCombobox: ComboBox[String] = _
  private var typeMap: util.LinkedHashMap[String, ScType] = _
  private var replaceOccurrencesChb: JCheckBox = _
  private var defaultValuesUsagePanel: DefaultValuesUsagePanel = _
  private var defaultForIntroducedTextField: EditorTextField = _

  override def init(): Unit = {
    super.init()
    setTitle(ScalaIntroduceParameterHandler.REFACTORING_NAME)
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
      new ScalaChangeInfo(getVisibility, method.fun, getMethodName, returnType, parameters, isAddDefaultArgs, None)

    val newData = introduceData.copy(paramName = paramNameField.getText, tp = typeMap.get(typeCombobox.getSelectedItem),
      replaceAll = replaceOccurrencesChb.isSelected, defaultArg = defaultForIntroducedTextField.getText)

    changeInfo.introducedParameterData = Some(newData)
    new ScalaChangeSignatureProcessor(project, changeInfo)
  }

  override def createOptionsPanel(): JComponent = {
    val panel = super.createOptionsPanel()
    panel.setVisible(false)
    panel
  }

  override def customizeParametersTable(table: TableView[ScalaParameterTableModelItem]): Unit = {
    table.setSelection(util.Collections.emptyList())
  }

  override protected def createParametersListTable: ParametersListTable = {
    new ScalaParametersListTable() {
      override def isRowEditable(row: Int): Boolean = false

      override protected def defaultText(item: ScalaParameterTableModelItem): String = ""
    }
  }

  override def getPreferredFocusedComponent: JComponent = paramNameField

  protected override def doValidate(): ValidationInfo = null

  override protected def decorateParameterTable(table: JBTable): JPanel = {
    table.setCellSelectionEnabled(false)
    table.setRowSelectionAllowed(true)
    table.getSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    table.setSurrendersFocusOnKeystroke(true)
    val buttonsPanel: JPanel =
      ToolbarDecorator.createDecorator(table)
              .setMoveUpAction(upAction)
              .setMoveDownAction(downAction)
              .disableAddAction()
              .disableRemoveAction()
              .addExtraActions(createAddClauseButton(), createRemoveClauseButton())
              .createPanel
    myParametersTableModel.addTableModelListener(mySignatureUpdater)
    buttonsPanel
  }

  private def createParamNamePanel(): JComponent = {
    paramNameField = new EditorTextField(introduceData.paramName)
    paramNameField.setPreferredWidth(150)
    paramNameField.addDocumentListener(new DocumentListener {
      override def documentChanged(e: DocumentEvent): Unit = {
        val newText: String = paramNameField.getText
        introducedParamTableItem.foreach(_.parameter.setName(newText))
        myParametersTableModel.fireTableDataChanged()
        parametersTable.updateUI()
        updateSignatureAlarmFired()
        getRefactorAction.setEnabled(!newText.isEmpty)
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
    implicit val context: TypePresentationContext = method.getMethod
    typeMap = ScalaRefactoringUtil.getCompatibleTypeNames(introduceData.possibleTypes)
    for (typeName <- typeMap.keySet.asScala) {
      JListCompatibility.addItem(typeCombobox, typeName)
    }
    typeLabel.setDisplayedMnemonic('T')
    typeCombobox.addItemListener(_ => {
      val scType = typeMap.get(typeCombobox.getSelectedItem)
      introducedParamTableItem.foreach { item =>
        item.parameter.scType = scType
        item.typeText = scType.codeText
      }
      myParametersTableModel.fireTableDataChanged()
      parametersTable.updateUI()
      updateSignatureAlarmFired()
    })
    val paramTypePanel = new JPanel(new BorderLayout(0, 2))
    paramTypePanel.add(typeLabel, BorderLayout.NORTH)
    IJSwingUtilities.adjustComponentsOnMac(typeLabel, typeCombobox)
    paramTypePanel.add(typeCombobox, BorderLayout.SOUTH)

    paramTypePanel
  }

  override def createDefaultArgumentPanel(): JPanel = {
    val panel = new JPanel(new BorderLayout())
    defaultForIntroducedTextField = new EditorTextField(introduceData.defaultArg, project, ScalaFileType.INSTANCE)
    val label = new JLabel("Default value:")
    label.setLabelFor(defaultForIntroducedTextField)
    panel.add(label, BorderLayout.NORTH)
    defaultForIntroducedTextField.setOneLineMode(false)
    defaultForIntroducedTextField.setEnabled(true)
    defaultForIntroducedTextField.addDocumentListener(new DocumentListener {
      override def documentChanged(e: DocumentEvent): Unit = {
        introducedParamTableItem.foreach(_.parameter.defaultValue = defaultForIntroducedTextField.getText.trim)
      }
    })
    IJSwingUtilities.adjustComponentsOnMac(label, defaultForIntroducedTextField)
    panel.add(defaultForIntroducedTextField, BorderLayout.CENTER)
    val optionsPanel = new JPanel(new BorderLayout())
    replaceOccurrencesChb = new JCheckBox("Replace all occurrences")
    replaceOccurrencesChb.setMnemonic('a')
    replaceOccurrencesChb.setSelected(false)
    replaceOccurrencesChb.setVisible(introduceData.occurrences.length > 1)
    optionsPanel.add(replaceOccurrencesChb, BorderLayout.NORTH)
    defaultValuesUsagePanel = new DefaultValuesUsagePanel("")
    optionsPanel.add(defaultValuesUsagePanel, BorderLayout.CENTER)
    panel.add(optionsPanel, BorderLayout.SOUTH)
    panel
  }

  private def introducedParamTableItem: Option[ScalaParameterTableModelItem] = {
    parameterItems.find(_.parameter.isIntroducedParameter)
  }

  override protected def getDefaultValuesPanel: DefaultValuesUsagePanel = defaultValuesUsagePanel
}
