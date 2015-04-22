package org.jetbrains.plugins.scala.lang.refactoring.changeSignature

import java.awt.event.MouseEvent
import java.awt.{BorderLayout, Color, Font, Toolkit}
import javax.swing.border.MatteBorder
import javax.swing.{JComponent, JPanel, JTable}

import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorFontType}
import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent}
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.table.JBTableRowEditor._
import com.intellij.util.ui.table.{JBTableRow, JBTableRowEditor}

import scala.collection.mutable.ArrayBuffer

/**
 * @author Nikolay.Tropin
 */
class ScalaChangeSignatureRowEditor(item: ScalaParameterTableModelItem, dialog: ScalaChangeSignatureDialog) extends JBTableRowEditor {

  private val project = dialog.project
  private val fileType = dialog.getFileType
  private val signatureUpdater = dialog.signatureUpdater
  private val backgroundColor = dialog.getContentPane.getBackground
  private val separatorColor: Color = dialog.clauseSeparatorColor

  private val table = dialog.parametersTable
  val typeDoc = PsiDocumentManager.getInstance(project).getDocument(item.typeCodeFragment)
  val myTypeEditor: EditorTextField = new EditorTextField(typeDoc, project, fileType)

  val myNameEditor: EditorTextField = new EditorTextField(item.parameter.getName, project, fileType)
  val defaultValueDoc = PsiDocumentManager.getInstance(project).getDocument(item.defaultValueCodeFragment)

  val myDefaultValueEditor = new EditorTextField(defaultValueDoc, project, fileType)

  def prepareEditor(table: JTable, row: Int) {
    setLayout(new BorderLayout)
    addNameEditor()
    addTypeEditor()
    val color = if (item.startsNewClause) separatorColor else backgroundColor
    setBorder(new MatteBorder(2, 0, 0, 0, color))

    if (!item.isEllipsisType && item.parameter.getOldIndex == -1) {
      val additionalPanel: JPanel = new JPanel(new BorderLayout)
      addDefaultValueEditor(additionalPanel)
      add(additionalPanel, BorderLayout.SOUTH)
    }
  }

  def addNameEditor() {
    myNameEditor.addDocumentListener(signatureUpdater)
    myNameEditor.setPreferredWidth(table.getWidth / 3)
    myNameEditor.addDocumentListener(new this.RowEditorChangeListener(0))
    add(createLabeledPanel("Name:", myNameEditor), BorderLayout.WEST)
  }

  def addTypeEditor() {
    myTypeEditor.addDocumentListener(signatureUpdater)
    myTypeEditor.addDocumentListener(new DocumentAdapter {
      override def documentChanged(e: DocumentEvent): Unit = {
        item.typeText = myTypeEditor.getText
      }
    })
    myTypeEditor.addDocumentListener(new this.RowEditorChangeListener(1))
    add(createLabeledPanel("Type:", myTypeEditor), BorderLayout.CENTER)
  }

  def getColumnWidth(letters: Int): Int = {
    val editorFont: Font = EditorColorsManager.getInstance.getGlobalScheme.getFont(EditorFontType.PLAIN)
    val font = new Font(editorFont.getFontName, editorFont.getStyle, 12)
    letters * Toolkit.getDefaultToolkit.getFontMetrics(font).stringWidth("W")
  }

  def getTypesColumnWidth: Int = getColumnWidth(dialog.getNamesMaxLength + 2 + dialog.getTypesMaxLength)

  def getNamesColumnWidth: Int = getColumnWidth(dialog.getNamesMaxLength + 2)

  def addDefaultValueEditor(additionalPanel: JPanel) {
    myDefaultValueEditor.setPreferredWidth(table.getWidth / 2)
    myDefaultValueEditor.addDocumentListener(new this.RowEditorChangeListener(2))
    myDefaultValueEditor.addDocumentListener(new DocumentAdapter {
      override def documentChanged(e: DocumentEvent): Unit = {
        item.parameter.defaultValue = myDefaultValueEditor.getText.trim
      }
    })
    additionalPanel.add(createLabeledPanel("Default value:", myDefaultValueEditor), BorderLayout.WEST)
  }

  def getValue: JBTableRow = {
    new JBTableRow {
      def getValueAt(column: Int): AnyRef = {
        column match {
          case 0 => myNameEditor.getText.trim
          case 1 => myTypeEditor.getText.trim
          case 2 => myDefaultValueEditor.getText.trim
          case _ => null
        }
      }
    }
  }

  def getPreferredFocusedComponent: JComponent = {
    val me: MouseEvent = getMouseEvent
    if (me == null) {
      return myNameEditor.getFocusTarget
    }
    val x: Double = me.getPoint.getX
    if (x <= getNamesColumnWidth) myNameEditor.getFocusTarget
    else if (myDefaultValueEditor == null || x <= getTypesColumnWidth) myTypeEditor.getFocusTarget
    else myDefaultValueEditor.getFocusTarget
  }

  def getFocusableComponents: Array[JComponent] = {
    val focusable = ArrayBuffer[JComponent]()
    focusable += myNameEditor.getFocusTarget
    focusable += myTypeEditor.getFocusTarget
    if (myDefaultValueEditor != null) {
      focusable += myDefaultValueEditor.getFocusTarget
    }
    focusable.toArray
  }
}
