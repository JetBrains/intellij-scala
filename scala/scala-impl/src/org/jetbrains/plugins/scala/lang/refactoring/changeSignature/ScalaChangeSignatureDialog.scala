package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.awt._
import java.awt.event.ActionEvent
import java.util

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightLevelUtil}
import com.intellij.openapi.actionSystem.{AnActionEvent, CustomShortcutSet}
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{util => _, _}
import com.intellij.refactoring.changeSignature.{CallerChooserBase, ChangeSignatureDialogBase, ParameterTableModelItemBase}
import com.intellij.refactoring.ui.{CodeFragmentTableCellEditorBase, StringTableCellEditor, VisibilityPanelBase}
import com.intellij.refactoring.{BaseRefactoringProcessor, RefactoringBundle}
import com.intellij.ui.table.{JBTable, TableView}
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{util => _, _}
import com.intellij.util.Consumer
import com.intellij.util.ui.table.{JBListTable, JBTableRowEditor, JBTableRowRenderer}
import com.intellij.util.ui.{StartupUiUtil, UIUtil}
import javax.swing._
import javax.swing.border.MatteBorder
import javax.swing.event.{ChangeEvent, HyperlinkEvent}
import javax.swing.table.TableCellEditor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils
import org.jetbrains.plugins.scala.lang.refactoring.ui.ScalaComboBoxVisibilityPanel
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
* Nikolay.Tropin
* 2014-08-29
*/
//noinspection ConvertNullInitializerToUnderscore
class ScalaChangeSignatureDialog(val method: ScalaMethodDescriptor,
                                 val needSpecifyTypeChb: Boolean)
                                (implicit val project: Project)
  extends {
    private var defaultValuesUsagePanel: DefaultValuesUsagePanel = null
    var mySpecifyTypeChb: JCheckBox = null
  }
    with ChangeSignatureDialogBase[ScalaParameterInfo,
    ScFunction,
    String,
    ScalaMethodDescriptor,
    ScalaParameterTableModelItem,
    ScalaParameterTableModel](project, method, false, method.fun) {

  override def getFileType: LanguageFileType = ScalaFileType.INSTANCE

  override def createCallerChooser(title: String, treeToReuse: Tree, callback: Consumer[util.Set[ScFunction]]): CallerChooserBase[ScFunction] = null

  override def createRefactoringProcessor(): BaseRefactoringProcessor = {
    val parameters = splittedItems.map(_.map(_.parameter))
        
    val changeInfo =
      ScalaChangeInfo(getVisibility, method.fun, getMethodName, returnType, parameters, isAddDefaultArgs, Some(mySpecifyTypeChb.isSelected))

    new ScalaChangeSignatureProcessor(changeInfo)
  }
  
  override def createNorthPanel(): JComponent = {
    val panel = super.createNorthPanel()
    getMethodName match {
      case "apply" | "unapply" | "unapplySeq" | "update" => myNameField.setEnabled(false)
      case _ =>
    }
    val holder = new JPanel(new BorderLayout())
    holder.add(panel, BorderLayout.NORTH)
    holder.add(createDefaultArgumentPanel(), BorderLayout.LINE_START)
    holder
  }

  protected def createDefaultArgumentPanel(): JPanel = {
    val optionsPanel = new JPanel(new BorderLayout())
    val label = new JLabel(ScalaBundle.message("parameter.label.default.value"))
    defaultValuesUsagePanel = new DefaultValuesUsagePanel("")
  
    val holder = new JPanel()
    holder.add(label)
    holder.add(defaultValuesUsagePanel)
  
    optionsPanel.add(holder)
    optionsPanel
  }
  
  override def createOptionsPanel(): JComponent = {
    val panel = super.createOptionsPanel() //to initialize fields in base class
    
    val holder: JPanel = new JPanel
    holder.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0))
    
    val specifyTypePanel = createTypePanel()
    panel.add(specifyTypePanel)
    
    panel.setVisible(needSpecifyTypeChb)
    myPropagateParamChangesButton.setVisible(false)
    
    panel
  }

  override def createVisibilityControl(): VisibilityPanelBase[String] = new ScalaComboBoxVisibilityPanel(getVisibility)

  override def createParametersInfoModel(method: ScalaMethodDescriptor): ScalaParameterTableModel = {
    new ScalaParameterTableModel(method.fun, method.fun, method)
  }

  override protected def createParametersPanel(hasTabsInDialog: Boolean): JPanel = {
    myParametersTable = createParametersTable()
    myParametersList = createParametersListTable()
    decorateParameterTable(myParametersList.getTable)
  }

  protected def createParametersTable(): TableView[ScalaParameterTableModelItem] = {
    new TableView[ScalaParameterTableModelItem](myParametersTableModel) {
      override def removeEditor(): Unit = {
        clearEditorListeners()
        super.removeEditor()
      }

      override def editingStopped(e: ChangeEvent): Unit = {
        super.editingStopped(e)
        repaint()
      }

      private def clearEditorListeners(): Unit = {
        val editor: TableCellEditor = getCellEditor
        editor match {
          case ed: StringTableCellEditor =>
            ed.clearListeners()
          case _ => editor match {
            case base: CodeFragmentTableCellEditorBase =>
              base.clearListeners()
            case _ =>
          }
        }
      }

      override def prepareEditor(editor: TableCellEditor, row: Int, column: Int): Component = {
        val listener: DocumentListener = new DocumentListener() {
          override def documentChanged(e: DocumentEvent): Unit = {
            val ed: TableCellEditor = parametersTable.getCellEditor
            if (ed != null) {
              val editorValue: AnyRef = ed.getCellEditorValue
              myParametersTableModel.setValueAtWithoutUpdate(editorValue, row, column)
              updateSignature()
            }
          }
        }
        editor match {
          case ed: StringTableCellEditor =>
            ed.addDocumentListener(listener)
          case _ => editor match {
            case base: CodeFragmentTableCellEditorBase =>
              base.addDocumentListener(listener)
            case _ =>
          }
        }
        super.prepareEditor(editor, row, column)
      }

      override def editingCanceled(e: ChangeEvent): Unit = {
        super.editingCanceled(e)
      }
    }
  }

  override def createReturnTypeCodeFragment(): PsiCodeFragment = {
    val text = method.returnTypeText
    val child = method.fun
    val fragment = ScalaCodeFragment(text, child.getParent, child)
    HighlightLevelUtil.forceRootHighlighting(fragment, FileHighlightingSetting.SKIP_HIGHLIGHTING)
    fragment
  }

  override protected def createParametersListTable: ParametersListTable = new ScalaParametersListTable()

  protected override def getTableEditor(t: JTable, item: ParameterTableModelItemBase[ScalaParameterInfo]): JBTableRowEditor = {
    val scalaItem = item match {
      case si: ScalaParameterTableModelItem => si
      case _ => throw new IllegalArgumentException
    }

    new ScalaChangeSignatureRowEditor(scalaItem, this)
  }

  private def needsTypeAnnotation(method: ScalaMethodDescriptor, visibilityString: String = getVisibility): Boolean = {
    val element = method.fun
    ScalaTypeAnnotationSettings(element.getProject).isTypeAnnotationRequiredFor(
      Declaration(element, Visibility(visibilityString)),
      Location(element),
      Some(Definition(element))
    )
  }

  override def calculateSignature(): String = {
    def nameAndType(item: ScalaParameterTableModelItem) = {
      if (item.parameter.name == "") ""
      else ScalaExtractMethodUtils.typedName(item.parameter.name, item.typeText)
    }

    def itemText(item: ScalaParameterTableModelItem) = item.keywordsAndAnnotations + nameAndType(item)

    val visibility = getVisibility
    val prefix = method.fun match {
      case fun: ScFunction =>
        val name = if (!fun.isConstructor) getMethodName else "this"
        s"$visibility def $name"
      case pc: ScPrimaryConstructor => s"class ${pc.getClassNameText} $getVisibility"
      case _ => ""
    }
    val paramsText = splittedItems.map(_.map(itemText).mkString("(", ", ", ")")).mkString

    val retTypeText = returnTypeText
  
    val needType =
      if (!needSpecifyTypeChb) true
      else if (mySpecifyTypeChb != null) mySpecifyTypeChb.isSelected
      else needsTypeAnnotation(method, visibility)
    
    val typeAnnot =
      if (retTypeText.isEmpty || !needType) ""
      else s": $retTypeText"

    s"$prefix$paramsText$typeAnnot"
  }

  override def validateAndCommitData(): String = {
    val paramItems = parameterItems
    val problems = mutable.ListBuffer.empty[String]

    if (myReturnTypeCodeFragment != null) {
      if (myReturnTypeCodeFragment.getText.isEmpty)
        problems += RefactoringBundle.message("changeSignature.no.return.type")
      else if (returnTypeText.isEmpty)
        problems += RefactoringBundle.message("changeSignature.wrong.return.type", myReturnTypeCodeFragment.getText)
    }

    val paramNames = paramItems.map(_.parameter.name)
    val names = if (myNameField.isEnabled) getMethodName +: paramNames else paramNames
    problems ++= names.collect {
      case name if !isIdentifier(name) => s"$name is not a valid scala identifier"
    }

    val namesWithIndices = paramNames.zipWithIndex
    for {
      (name, idx) <- namesWithIndices
      (name2, idx2) <- namesWithIndices
      if name == name2 && idx < idx2
    } {
      problems += ScalaBundle.message("change.signature.parameters.same.name.{0}", name)
    }
    paramItems.foreach(_.updateType(problems))

    paramItems.foreach {
      case item if item.parameter.isRepeatedParameter && !splittedItems.flatMap(_.lastOption).contains(item) =>
        problems += ScalaBundle.message("change.signature.vararg.should.be.last.in.clause")
      case _ =>
    }

    if (problems.isEmpty) null
    else problems.distinct.mkString("\n")
  }

  protected override def doValidate(): ValidationInfo = {
    if (!getTableComponent.isEditing) {
      for {
        item <- parameterItems
        if item.parameter.oldIndex < 0 && StringUtil.isEmpty(item.defaultValueCodeFragment.getText)
      } {
        return new ValidationInfo(
          if (isAddDefaultArgs) ScalaBundle.message("default.value.is.missing.default.arguments")
          else ScalaBundle.message("default.value.is.missing.method.calls")
        )
      }
    }
    super.doValidate()
  }

  override def updateSignatureAlarmFired(): Unit = {
    super.updateSignatureAlarmFired()

    if (getDefaultValuesPanel != null) {
      if (parameterItems.exists(_.typeText.endsWith("*"))) getDefaultValuesPanel.forceIsModifyCalls()
      else getDefaultValuesPanel.release()
    }
  }

  override def dispose(): Unit = {
    myParametersTableModel.clear()
    super.dispose()
  }

  override def mayPropagateParameters(): Boolean = false

  override def isListTableViewSupported: Boolean = true

  override protected def postponeValidation: Boolean = false

  def signatureUpdater: ChangeSignatureDialogBase[ScalaParameterInfo, ScFunction, String, ScalaMethodDescriptor, ScalaParameterTableModelItem, ScalaParameterTableModel]#UpdateSignatureListener = mySignatureUpdater

  def getTypesMaxLength: Int = {
    parameterItems.map(_.typeText.length) match {
      case Seq() => 0
      case seq => seq.max
    }
  }

  def getNamesMaxLength: Int = {
    parameterItems.map(_.parameter.getName.length) match {
      case Seq() => 0
      case seq => seq.max
    }
  }

  def parametersTable: JBTable = Option(myParametersList).map(_.getTable).orNull

  protected def getDefaultValuesPanel: DefaultValuesUsagePanel = defaultValuesUsagePanel

  protected def isAddDefaultArgs: Boolean = getDefaultValuesPanel.isAddDefaultArgs

  protected def returnTypeText: String = Option(myReturnTypeCodeFragment).fold("")(_.getText)

  protected def returnType: ScType =
    Option(myReturnTypeCodeFragment).flatMap { fragment =>
      createTypeFromText(fragment.getText, fragment.getContext, fragment)
    }.getOrElse(api.Any)

  protected def splittedItems: Seq[Seq[ScalaParameterTableModelItem]] = {
    def inner(items: Seq[ScalaParameterTableModelItem]): Seq[Seq[ScalaParameterTableModelItem]] = {
      if (items.isEmpty) return Seq(items)

      val index = items.tail.indexWhere(_.startsNewClause)
      if (index < 0) Seq(items)
      else {
        val (firstClause, rest) = items.splitAt(index + 1)
        firstClause +: inner(rest)
      }
    }
    inner(parameterItems)
  }

  protected def parameterItems: Seq[ScalaParameterTableModelItem] = {
    myParametersTableModel.getItems.asScala
  }

  protected def createAddClauseButton(): AnActionButton = {
    val addClauseButton = new AnActionButton(ScalaBundle.message("change.signature.add.parameter.clause"), null, Icons.ADD_CLAUSE) {
      override def actionPerformed(e: AnActionEvent): Unit = {
        val table = parametersTable
        val editedColumn = editingColumn(table)
        TableUtil.stopEditing(table)
        val selected = table.getSelectedRow
        if (selected > 0) {
          val item = myParametersTableModel.getItem(selected)
          item.startsNewClause = true
          myParametersTableModel.fireTableDataChanged()
        }
        finishAndRestoreEditing(editedColumn)
      }
    }
    addClauseButton.addCustomUpdater((e: AnActionEvent) => {
      val selected = parametersTable.getSelectedRow
      selected > 0 && !myParametersTableModel.getItem(selected).startsNewClause
    })
    addClauseButton.setShortcut(CustomShortcutSet.fromString("alt EQUALS"))
    addClauseButton
  }

  protected def createRemoveClauseButton(): AnActionButton = {
    val removeClauseButton = new AnActionButton(ScalaBundle.message("change.signature.remove.parameter.clause"), null, Icons.REMOVE_CLAUSE) {
      override def actionPerformed(e: AnActionEvent): Unit = {
        val table = parametersTable
        val editedColumn = editingColumn(table)
        TableUtil.stopEditing(table)
        val selected = table.getSelectedRow
        if (selected > 0) {
          val item = myParametersTableModel.getItem(selected)
          item.startsNewClause = false
          myParametersTableModel.fireTableDataChanged()
        }
        finishAndRestoreEditing(editedColumn)
      }
    }
    removeClauseButton.addCustomUpdater((e: AnActionEvent) => {
      val selected = parametersTable.getSelectedRow
      selected > 0 && myParametersTableModel.getItem(selected).startsNewClause
    })
    removeClauseButton.setShortcut(CustomShortcutSet.fromString("alt MINUS"))
    removeClauseButton
  }

  protected def downAction: AnActionButtonRunnable = new AnActionButtonRunnable {
    override def run(t: AnActionButton): Unit = {
      val table = parametersTable
      val selected = table.getSelectedRow
      if (selected < 0 || selected >= table.getModel.getRowCount - 1) return
      val editedColumn = editingColumn(table)
      TableUtil.stopEditing(table)

      val itemBelow = myParametersTableModel.getItem(selected + 1)
      val item = myParametersTableModel.getItem(selected)
      if (itemBelow.startsNewClause) {
        itemBelow.startsNewClause = false
        if (selected > 0) item.startsNewClause = true
        myParametersTableModel.fireTableDataChanged()
      }
      else {
        if (item.startsNewClause) {
          item.startsNewClause = false
          itemBelow.startsNewClause = true
        }
        myParametersTableModel.exchangeRows(selected, selected + 1)
        table.setRowSelectionInterval(selected + 1, selected + 1)
      }
      finishAndRestoreEditing(editedColumn)
    }
  }

  protected def upAction: AnActionButtonRunnable = new AnActionButtonRunnable {
    override def run(t: AnActionButton): Unit = {
      val table = parametersTable
      val selected = table.getSelectedRow
      if (selected <= 0 || selected >= table.getModel.getRowCount) return
      val editedColumn = editingColumn(table)
      TableUtil.stopEditing(table)
      val item = myParametersTableModel.getItem(selected)
      if (item.startsNewClause) {
        item.startsNewClause = false
        if (selected != table.getModel.getRowCount - 1) {
          val itemBelow = myParametersTableModel.getItem(selected + 1)
          itemBelow.startsNewClause = true
        }
        myParametersTableModel.fireTableDataChanged()
      }
      else {
        val itemAbove = myParametersTableModel.getItem(selected - 1)
        if (itemAbove.startsNewClause) {
          itemAbove.startsNewClause = false
          item.startsNewClause = true
        }
        myParametersTableModel.exchangeRows(selected, selected - 1)
        table.setRowSelectionInterval(selected - 1, selected - 1)
      }
      finishAndRestoreEditing(editedColumn)
    }
  }

  protected def decorateParameterTable(table: JBTable): JPanel = {
    table.setCellSelectionEnabled(true)
    table.getSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    table.getSelectionModel.setSelectionInterval(0, 0)
    table.setSurrendersFocusOnKeystroke(true)
    val buttonsPanel: JPanel =
      ToolbarDecorator.createDecorator(table)
              .setMoveUpAction(upAction)
              .setMoveDownAction(downAction)
              .addExtraActions(createAddClauseButton(), createRemoveClauseButton())
              .createPanel
    myParametersTableModel.addTableModelListener(mySignatureUpdater)
    buttonsPanel
  }

  private def finishAndRestoreEditing(editedColumn: Option[Int]): Unit = {
    val table = parametersTable
    TableUtil.updateScroller(table)
    table.requestFocus()
    editedColumn.foreach { col =>
      val row = table.getSelectedRow
      table.setRowHeight(row, table.getRowHeight)
      table.editCellAt(row, col)
    }
  }

  def clauseSeparatorColor: Color = {
    val background = getContentPane.getBackground
    if (StartupUiUtil.isUnderDarcula) background.brighter.brighter else background.darker()
  }

  private def editingColumn(table: JTable) = if (table.isEditing) Some(table.getEditingColumn) else None
  
  private def createTypePanel(): JPanel = {
    val typePanel = new JPanel
    typePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))
    
    mySpecifyTypeChb = new JCheckBox
    mySpecifyTypeChb.setText("Specify result type")
    mySpecifyTypeChb.setDisplayedMnemonicIndex(15)

    typePanel.add(mySpecifyTypeChb)
    
    val myLinkContainer = new JPanel
    myLinkContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))
    typePanel.add(myLinkContainer)
    
    myLinkContainer.add(setUpHyperLink())
    
    setUpSpecifyTypeChb()
    setUpVisibilityListener()
    
    typePanel
  }
  
  private def setUpHyperLink(): HyperlinkLabel = {
    val link = TypeAnnotationUtil.createTypeAnnotationsHLink(project, ScalaBundle.message("default.ta.settings"))

    link.addHyperlinkListener((e: HyperlinkEvent) => {
      extensions.invokeLater {
        mySpecifyTypeChb.setSelected(needsTypeAnnotation(method))
        updateSignatureAlarmFired()
      }
    })
    
    link
  }
  
  private def setUpVisibilityListener(): Unit = {
    myVisibilityPanel.addListener((e: ChangeEvent) => {
      mySpecifyTypeChb.setSelected(needsTypeAnnotation(method))
      updateSignatureAlarmFired()
    })
  }
  
  private def setUpSpecifyTypeChb(): Unit ={
    mySpecifyTypeChb.setSelected(needsTypeAnnotation(method))
    
    mySpecifyTypeChb.addActionListener((e: ActionEvent) => updateSignatureAlarmFired())
  }
  
  class ScalaParametersListTable extends ParametersListTable {
    override protected def getRowRenderer(row: Int): JBTableRowRenderer = {
      (table: JTable, row: Int, selected: Boolean, focused: Boolean) => {
        val item = getRowItem(row)
        val name = nameText(item)
        val typeTxt = typeText(item)
        val nameAndType =
          if (name == "" && typeTxt == "") ""
          else ScalaExtractMethodUtils.typedName(name, typeTxt)
        val defText = defaultText(item)
        val text = s"$nameAndType $defText"
        val comp = JBListTable.createEditorTextFieldPresentation(project, getFileType, " " + text, selected, focused)

        if (item.parameter.isIntroducedParameter) {
          val fields = UIUtil.findComponentsOfType(comp, classOf[EditorTextField]).asScala
          fields.foreach { f =>
            f.setFont(f.getFont.deriveFont(Font.BOLD))
          }
        }

        val color =
          if (item.startsNewClause) clauseSeparatorColor
          else if (selected && focused) parametersTable.getSelectionBackground else parametersTable.getBackground

        comp.setBorder(new MatteBorder(2, 0, 0, 0, color))
        comp
      }
    }

    protected def nameText(item: ScalaParameterTableModelItem): String = {
      val maxLength = parameterItems.map(_.parameter.getName.length) match {
        case Seq() => 0
        case seq => seq.max
      }
      val name = item.parameter.getName
      name + StringUtil.repeat(" ", maxLength - name.length)
    }

    protected def typeText(item: ScalaParameterTableModelItem): String = {
      val maxLength = parameterItems.map(_.typeText.length) match {
        case Seq() => 0
        case seq => seq.max
      }
      val typeText = item.typeText
      typeText + StringUtil.repeat(" ", maxLength - typeText.length)
    }

    protected def defaultText(item: ScalaParameterTableModelItem): String = {
      val defaultValue: String = item.defaultValueCodeFragment.getText
      if (StringUtil.isNotEmpty(defaultValue)) " = " + defaultValue else ""
    }

    override protected def isRowEmpty(row: Int): Boolean = false

    override def getRowItem(row: Int): ScalaParameterTableModelItem = myParametersTableModel.getRowValue(row)

    override def getRowEditor(item: ParameterTableModelItemBase[ScalaParameterInfo]): JBTableRowEditor = getTableEditor(getTable, item)
  }

}

