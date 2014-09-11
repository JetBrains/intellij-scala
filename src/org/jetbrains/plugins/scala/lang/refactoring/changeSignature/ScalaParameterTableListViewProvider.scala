package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.awt.event.MouseEvent
import java.awt.{BorderLayout, Font, Toolkit}
import javax.swing.{JComponent, JPanel, JTable}

import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorFontType}
import com.intellij.openapi.editor.event.{DocumentAdapter, DocumentEvent}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.table.{JBListTable, JBTableRow, JBTableRowEditor}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * Nikolay.Tropin
 * 2014-09-01
 */
private [changeSignature] trait ScalaParameterTableListViewProvider {
  this: ScalaChangeSignatureDialog =>

  protected override def getTableEditor(t: JTable, item: ParameterTableModelItemBase[ScalaParameterInfo]): JBTableRowEditor = {
    import com.intellij.util.ui.table.JBTableRowEditor._
    new JBTableRowEditor {
      val scalaItem = item match {
        case si: ScalaParameterTableModelItem => si
        case _ => throw new IllegalArgumentException
      }

      val typeDoc = PsiDocumentManager.getInstance(project).getDocument(item.typeCodeFragment)
      val myTypeEditor: EditorTextField = new EditorTextField(typeDoc, project, getFileType)
      val myNameEditor: EditorTextField = new EditorTextField(item.parameter.getName, project, getFileType)

      val defaultValueDoc = PsiDocumentManager.getInstance(project).getDocument(item.defaultValueCodeFragment)
      val myDefaultValueEditor = new EditorTextField(defaultValueDoc, project, getFileType)

      def prepareEditor(table: JTable, row: Int) {
        setLayout(new BorderLayout)
        addNameEditor()
        addTypeEditor()

        if (!item.isEllipsisType && item.parameter.getOldIndex == -1) {
          val additionalPanel: JPanel = new JPanel(new BorderLayout)
          addDefaultValueEditor(additionalPanel)
          add(additionalPanel, BorderLayout.SOUTH)
        }
      }

      def addNameEditor() {
        myNameEditor.addDocumentListener(signatureUpdater)
        myNameEditor.setPreferredWidth(t.getWidth / 3)
        myNameEditor.addDocumentListener(new this.RowEditorChangeListener(0))
        add(createLabeledPanel("Name:", myNameEditor), BorderLayout.WEST)
      }

      def addTypeEditor() {
        myTypeEditor.addDocumentListener(signatureUpdater)
        myTypeEditor.addDocumentListener(new DocumentAdapter {
          override def documentChanged(e: DocumentEvent): Unit = {
            scalaItem.typeText = myTypeEditor.getText
          }
        })
        myTypeEditor.addDocumentListener(new this.RowEditorChangeListener(1))
        add(createLabeledPanel("Type:", myTypeEditor), BorderLayout.CENTER)
      }

      def addDefaultValueEditor(additionalPanel: JPanel) {
        myDefaultValueEditor.setPreferredWidth(t.getWidth / 2)
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
  }

  protected override def getRowPresentation(item: ParameterTableModelItemBase[ScalaParameterInfo], selected: Boolean, focused: Boolean): JComponent = {
    val name = {
      val name1 = item.parameter.getName
      name1 + StringUtil.repeat(" ", getNamesMaxLength - name1.length)
    }
    val typeText: String = {
      val text = item.asInstanceOf[ScalaParameterTableModelItem].typeText
      text + StringUtil.repeat(" ", getTypesMaxLength - text.length)
    }
    val nameAndType =
      if (name == "" && typeText == "") ""
      else ScalaExtractMethodUtils.typedName(name, typeText, project, byName/*already in type text*/ = false)
    val defaultValue: String = item.defaultValueCodeFragment.getText
    val defValue = if (StringUtil.isNotEmpty(defaultValue)) " = " + defaultValue else ""
    val text = s"$nameAndType $defValue"
    JBListTable.createEditorTextFieldPresentation(project, getFileType, " " + text, selected, focused)
  }

  private def getTypesMaxLength: Int = {
    parametersTableModel.getItems.asScala.map(_.typeText.length) match {
      case Seq() => 0
      case seq => seq.max
    }
  }

  private def getNamesMaxLength: Int = {
    parametersTableModel.getItems.asScala.map(_.parameter.getName.length) match {
      case Seq() => 0
      case seq => seq.max
    }
  }

  private def getColumnWidth(letters: Int): Int = {
    val editorFont: Font = EditorColorsManager.getInstance.getGlobalScheme.getFont(EditorFontType.PLAIN)
    val font = new Font(editorFont.getFontName, editorFont.getStyle, 12)
    letters * Toolkit.getDefaultToolkit.getFontMetrics(font).stringWidth("W")
  }

  private def getTypesColumnWidth: Int = getColumnWidth(getNamesMaxLength + 2 + getTypesMaxLength)

  private def getNamesColumnWidth: Int = getColumnWidth(getNamesMaxLength + 2)
}
