package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import java.awt.Font
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ParameterTableModelBase.NameColumn
import com.intellij.ui.{ColoredTableCellRenderer, SimpleTextAttributes}
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature._
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterTableModel.{ScalaNameColumn, ScalaTypeColumn}

/**
 * @author Nikolay.Tropin
 */
class ScalaIntroduceParameterTableModel(typeContext: PsiElement,
                                        defaultValueContext: PsiElement,
                                        methodDescriptor: ScalaMethodDescriptor,
                                        columnInfos: ColumnInfo[_, _]*)
        extends ScalaParameterTableModel(typeContext, defaultValueContext, methodDescriptor, columnInfos: _*) {

  def this(typeContext: PsiElement, defaultValueContext: PsiElement, methodDescriptor: ScalaMethodDescriptor) {
    this(typeContext, defaultValueContext, methodDescriptor: ScalaMethodDescriptor,
      new ScalaNameColumn(typeContext.getProject), new ScalaTypeColumn(typeContext.getProject))
  }

  override def createRowItem(parameterInfo: ScalaParameterInfo): ScalaParameterTableModelItem = {
    val info = Option(parameterInfo).getOrElse(ScalaParameterInfo(project))

    val paramTypeCodeFragment = new ScalaCodeFragment(project, info.typeText)

    paramTypeCodeFragment.setContext(typeContext.getParent, typeContext)
    new ScalaParameterTableModelItem(info, paramTypeCodeFragment, null)
  }
}

object ScalaIntroduceParameterTableModel {

  class ScalaNameColumn(project: Project) extends NameColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project) {
    override def isCellEditable(pParameterTableModelItemBase: ScalaParameterTableModelItem): Boolean = false

    override def valueOf(item: ScalaParameterTableModelItem): String = item.parameter.name

    override def doCreateRenderer(item: ScalaParameterTableModelItem): TableCellRenderer = customizedRenderer(item)
  }

  class ScalaTypeColumn(project: Project) extends NameColumn[ScalaParameterInfo, ScalaParameterTableModelItem](project, RefactoringBundle.message("column.name.type")) {
    override def isCellEditable(pParameterTableModelItemBase: ScalaParameterTableModelItem): Boolean = false

    override def doCreateRenderer(item: ScalaParameterTableModelItem): TableCellRenderer = customizedRenderer(item)

    override def valueOf(item: ScalaParameterTableModelItem): String = item.parameter.typeText
  }

  private def customizedRenderer(item: ScalaParameterTableModelItem) = new ColoredTableCellRenderer() {
    def customizeCellRenderer(table: JTable, value: AnyRef, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (value == null) return
      val font = table.getModel match {
        case m: ScalaIntroduceParameterTableModel if m.getItem(row).parameter.isIntroducedParameter => Font.BOLD
        case _ => Font.PLAIN
      }
      append(value.asInstanceOf[String], new SimpleTextAttributes(font, null))
    }
  }
}