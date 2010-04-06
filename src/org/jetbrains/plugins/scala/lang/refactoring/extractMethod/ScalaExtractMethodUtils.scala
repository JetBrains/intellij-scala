package org.jetbrains.plugins.scala.lang
package refactoring.extractMethod

import _root_.com.intellij.psi._
import _root_.com.intellij.refactoring.rename.RenameProcessor
import _root_.org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern}
import _root_.org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import _root_.org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReturnStmt, ScAssignStmt}
import _root_.org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, Nothing, ScType}
import _root_.org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
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
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import com.intellij.psi.impl.source.PsiClassReferenceType
import java.lang.String
import collection.mutable.ArrayBuffer
import psi.api.ScalaRecursiveElementVisitor

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

object ScalaExtractMethodUtils {
  def createMethodFromSettings(settings: ScalaExtractMethodSettings): ScFunction = {
    var builder: StringBuilder = new StringBuilder
    builder.append(settings.visibility)
    builder.append("def ").append(settings.methodName)
    if (settings.parameters.filter(_.passAsParameter).length != 0) {
      builder.append("(")
      for (param <- settings.parameters) {
        if (param.passAsParameter) {
          builder.append(if (!param.needMirror) param.oldName else "_" + param.newName).append(": ").
                  append(ScType.presentableText(param.tp)).append(", ")
        }
      }
      builder.delete(builder.length - 2, builder.length)
      builder.append(")")
    }
    builder.append(": ")
    builder.append(settings.calcReturnType)
    builder.append(" = {\n")
    for (param <- settings.parameters) {
      if (!param.passAsParameter) {
        builder.append(if (param.needMirror) "var " else "val ").append(param.oldName).append(": ").
                append(ScType.presentableText(param.tp)).
                append(" = _\n")
      } else if (param.needMirror) {
        builder.append("var ").append(param.oldName).append(": ").append(ScType.presentableText(param.tp)).
                append(" = _").append(param.newName).append("\n")
      }
    }
    val offset = builder.length
    for (element <- settings.elements) {
      builder.append(element.getText)
    }
    if (!settings.lastReturn) {
      settings.returnType match {
        case Some(psi.types.Unit) => {
          if (settings.returns.length == 1) {
            builder.append("\n(false, Some(").append(settings.returns(0).oldParamName).append("))")
          }
          else if (settings.returns.length > 1) {
            builder.append("\n(false, Some(").append(settings.returns.map(_.oldParamName).mkString("(", ", ", ")")).
              append("))")
          } else builder.append("\nfalse")
        }
        case Some(tp) => {
          if (settings.returns.length == 1) {
            builder.append("\n(None, Some(").append(settings.returns(0).oldParamName).append("))")
          }
          else if (settings.returns.length > 1) {
            builder.append("\n(None, Some(").append(settings.returns.map(_.oldParamName).mkString("(", ", ", ")")).
              append("))")
          } else builder.append("\nNone")
        }
        case _ => {
          if (settings.returns.length == 1) {
            builder.append("\n").append(settings.returns(0).oldParamName)
          }
          else if (settings.returns.length > 1) {
            builder.append("\n").append(settings.returns.map(_.oldParamName).mkString("(", ", ", ")"))
          }
        }
      }
    }
    builder.append("\n}")
    val method = ScalaPsiElementFactory.createMethodFromText(builder.toString, settings.elements.apply(0).getManager)


    val returnVisitor = new ScalaRecursiveElementVisitor {
      override def visitReturnStatement(ret: ScReturnStmt): Unit = {
        if (ret.returnFunction != Some(method)) return
        val text = ret.expr match {
          case Some(t) if !settings.lastReturn => "Some(" + t.getText + ")"
          case Some(t) => t.getText
          case _ if !settings.lastReturn => "true"
          case _ => ""
        }
        val retText = new StringBuilder("return ")
        if (settings.returns.length == 0 || settings.lastReturn) retText.append(text)
        else retText.append("(").append(text).append(", None)")
        val retElem = ScalaPsiElementFactory.createExpressionFromText(retText.toString, ret.getManager)
        ret.replace(retElem)
        super.visitReturnStatement(ret)
      }
    }
    returnVisitor.visitElement(method: ScalaPsiElement)

    settings.returnType match {
      case Some(tp) => {

      }
      case _ => //Nothing to do
    }

    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReference(ref: ScReferenceElement) = {
        ref.bind match {
          case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) =>{
            if (named.getContainingFile == method.getContainingFile && named.getTextOffset < offset &&
                    !named.getName.startsWith("_")) {
              val oldName = named.getName
              var break = false
              for (param <- settings.parameters if !break) {
                if (param.oldName == oldName) {
                  if (param.oldName != param.newName) {
                    val newRef = ScalaPsiElementFactory.createExpressionFromText(param.newName, method.getManager)
                    ref.getParent.getNode.replaceChild(ref.getNode, newRef.getNode)
                  }
                  break = true
                }
              }
            }
          }
          case _ =>
        }
      }
    }
    visitor.visitElement(method)

    val bindTo = new ArrayBuffer[(PsiNamedElement, String)]
    val newVisitor = new ScalaRecursiveElementVisitor() {
      override def visitElement(element: ScalaPsiElement) = {
        element match {
          case named: PsiNamedElement if named != method && named.getTextOffset < offset => {
            var break = false
            for (param <- settings.parameters if !break) {
              if (param.oldName == named.getName) {
                if (param.oldName != param.newName) {
                  bindTo += Tuple(named, param.newName)
                }
                break = true
              }
            }
          }
          case _ =>
        }
        super.visitElement(element)
      }
    }
    newVisitor.visitElement(method)
    for ((named, newName) <- bindTo) {
      val id = named.asInstanceOf[ScNamedElement].nameId
      id.getParent.getNode.replaceChild(id.getNode, ScalaPsiElementFactory.createIdentifier(newName, id.getManager))
    }
    method
  }

  class FakePsiType(val tp: ScType) extends PsiPrimitiveType("fakeForScala", PsiAnnotation.EMPTY_ARRAY) {
    override def getPresentableText: String = ScType.presentableText(tp)
  }

  class ScalaVariableData(val vari: ScTypedDefinition, val isMutable: Boolean, val isInsideOfElements: Boolean) extends {
    val tp = vari.getType(TypingContext.empty).getOrElse(org.jetbrains.plugins.scala.lang.psi.types.Nothing)
    val param = new FakePsiParameter(vari.getManager, ScalaFileType.SCALA_LANGUAGE, tp, vari.getName)
  } with ParameterTablePanel.VariableData(param, new FakePsiType(tp)) {
    passAsParameter = true
    name = param.getName
  }

  def convertVariableData(variable: VariableInfo, elements: Array[PsiElement]): ParameterTablePanel.VariableData = {
    var isMutable = false
    val definition = variable.element.asInstanceOf[ScTypedDefinition]
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) = {
        if (ScalaPsiUtil.isLValue(ref)) {
          ref.bind match {
            case Some(ScalaResolveResult(elem, _)) if elem == definition => isMutable = true
            case _ =>
          }
        }
        super.visitReference(ref)
      }
    }
    for (element <- elements if element.isInstanceOf[ScalaPsiElement]) {
      visitor.visitElement(element.asInstanceOf[ScalaPsiElement])
    }
    val isInside = if (elements.length > 0) {
      val startOffset = elements(0).getTextRange.getStartOffset
      val endOffset = elements(elements.length - 1).getTextRange.getEndOffset
      definition.getTextOffset >= startOffset && definition.getTextOffset < endOffset
    } else false
    new ScalaVariableData(definition, isMutable, isInside)
  }

  def calcReturnType(returnType: Option[ScType], returns: Array[ExtractMethodReturn], lastReturn: Boolean): String = {
    if (lastReturn) {
      return ScType.presentableText(returnType.get)
    }
    returnType match {
      case Some(psi.types.Unit) => {
        if (returns.length == 0) "Boolean"
        if (returns.length == 1) "(Boolean, Option[" + ScType.presentableText(returns.apply(0).returnType) + "])"
        else "(Boolean, Option[" + returns.map(r => ScType.presentableText(r.returnType)).mkString("(", ", ", ")") + "])"
      }
      case Some(tp) => {
        if (returns.length == 0) "Option[" + ScType.presentableText(tp) + "]"
        if (returns.length == 1) "(Option[" + ScType.presentableText(tp) +
                "], Option[" + ScType.presentableText(returns.apply(0).returnType) + "])"
        else "(Option[" + ScType.presentableText(tp) +"]," +
                " Option[" + returns.map(r => ScType.presentableText(r.returnType)).mkString("(", ", ", ")") + "])"
      }
      case None => {
        if (returns.length == 1) ScType.presentableText(returns.apply(0).returnType)
        else if (returns.length == 0) "Unit"
        else returns.map(r => ScType.presentableText(r.returnType)).mkString("(", ", ", ")")
      }
    }
  }
}