package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.ui.popup.{JBPopupFactory, JBPopupAdapter, LightweightWindowEvent}
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import javax.swing.{DefaultListModel, DefaultListCellRenderer, JList}
import java.awt.Component
import javax.swing.event.{ListSelectionListener, ListSelectionEvent}
import java.util.ArrayList
import com.intellij.openapi.util.Pass
import com.intellij.refactoring.util.ParameterTablePanel
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{Nothing, ScType}
import com.intellij.psi.impl.source.PsiClassReferenceType
import java.lang.String
import com.intellij.psi.{PsiAnnotation, PsiPrimitiveType, PsiElement, PsiExpression}

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

object ScalaExtractMethodUtils {
  def createMethodFromSettings(settings: ScalaExtractMethodSettings): ScFunction = {
    var builder: StringBuilder = new StringBuilder
    builder.append(settings.visibility)
    builder.append("def ").append(settings.methodName)
    if (settings.paramNames.length != 0) {
      builder.append(settings.paramNames.zip(settings.paramTypes.map(ScType.presentableText(_))).map(p =>
        p._1 + " : " + p._2).mkString("(", ", ", ")"))
    }
    builder.append(": ")
    if (settings.returnTypes.length == 1) builder.append(ScType.presentableText(settings.returnTypes.apply(0)))
    else if (settings.returnTypes.length == 0) builder.append("Unit")
    else builder.append(settings.returnTypes.map(ScType.presentableText(_)).mkString("(", ", ", ")"))
    builder.append(" = {\n")
    for (element <- settings.elements) {
      builder.append(element.getText)
    }
    builder.append("\n}")
    ScalaPsiElementFactory.createMethodFromText(builder.toString, settings.elements.apply(0).getManager)
  }

  //todo: Move to refactoring utils.
  def showChooser[T <: PsiElement](editor: Editor, elements: Array[T], pass: PsiElement => Unit, title: String, elementName: T => String): Unit = {
    val highlighter: ScopeHighlighter = new ScopeHighlighter(editor)
    val model: DefaultListModel = new DefaultListModel
    for (element <- elements) {
      model.addElement(element)
    }
    val list: JList = new JList(model)
    list.setCellRenderer(new DefaultListCellRenderer {
      override def getListCellRendererComponent(list: JList, value: Object, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
        val rendererComponent: Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val buf: StringBuffer = new StringBuffer
        val element: T = value.asInstanceOf[T]
        if (element.isValid) {
          setText(elementName(element))
        }
        return rendererComponent
      }
    })
    list.addListSelectionListener(new ListSelectionListener {
      def valueChanged(e: ListSelectionEvent): Unit = {
        highlighter.dropHighlight
        val index: Int = list.getSelectedIndex
        if (index < 0) return
        val element: T = model.get(index).asInstanceOf[T]
        val toExtract: ArrayList[PsiElement] = new ArrayList[PsiElement]
        toExtract.add(element)
        highlighter.highlight(element, toExtract)
      }
    })
    JBPopupFactory.getInstance.createListPopupBuilder(list).setTitle(title).setMovable(false).setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable {
      def run: Unit = {
        pass(list.getSelectedValue.asInstanceOf[T])
      }
    }).addListener(new JBPopupAdapter {
      override def onClosed(event: LightweightWindowEvent): Unit = {
        highlighter.dropHighlight
      }
    }).createPopup.showInBestPositionFor(editor)
  }

  def convertVariableData(variable: VariableInfo): ParameterTablePanel.VariableData = {
    val tp = variable.element.asInstanceOf[ScTypedDefinition].getType(TypingContext.empty).
            getOrElse(org.jetbrains.plugins.scala.lang.psi.types.Nothing)
    new ParameterTablePanel.VariableData(new FakePsiParameter(variable.element.getManager, ScalaFileType.SCALA_LANGUAGE,
      tp, variable.element.getName), new PsiPrimitiveType("fake", PsiAnnotation.EMPTY_ARRAY) {
      override def getPresentableText: String = ScType.presentableText(tp)
    })
  }
}