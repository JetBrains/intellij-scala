package org.jetbrains.plugins.scala.lang
package refactoring.extractMethod

import _root_.com.intellij.psi._
import _root_.com.intellij.refactoring.util.ParameterTablePanel.VariableData
import _root_.org.jetbrains.annotations.Nullable
import _root_.org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import _root_.org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import java.util.ArrayList
import com.intellij.refactoring.util.ParameterTablePanel
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import java.lang.String
import collection.mutable.ArrayBuffer
import psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor}
import psi.api.toplevel.{ScTypeParametersOwner, ScNamedElement, ScTypedDefinition}
import psi.api.expr._
import psi.api.statements.{ScValue, ScFunction}
import psi.api.statements.params.{ScParameter, ScTypeParam}
import scala.util.Sorting
import psi.types.nonvalue.Parameter
import psi.types._
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

object ScalaExtractMethodUtils {
  def createMethodFromSettings(settings: ScalaExtractMethodSettings): ScFunction = {
    val builder: StringBuilder = new StringBuilder
    builder.append(settings.visibility)
    builder.append("def ").append(settings.methodName)
    val tp: ArrayBuffer[ScTypeParam] = new ArrayBuffer
    var elem: PsiElement = settings.elements.apply(0)
    while (elem != null && !(elem.getTextRange.contains(settings.nextSibling.getTextRange) &&
            !elem.getTextRange.equalsToRange(settings.nextSibling.getTextRange.getStartOffset,
              settings.nextSibling.getTextRange.getEndOffset))) {
      elem match {
        case tpo: ScTypeParametersOwner => tp ++= tpo.typeParameters
        case _ =>
      }
      elem = elem.getParent
    }
    if (tp.length != 0) {
      builder.append(tp.reverse.map(_.getText).mkString("[", ", ", "]"))
    }
    if (settings.parameters.filter(_.passAsParameter).length != 0) {
      builder.append("(")
      for (param <- settings.parameters) {
        if (param.passAsParameter) {
          builder.append(if (!param.needMirror) param.oldName else "_" + param.newName).append(": ").
                  append(if (param.isCallByNameParameter) "=> " else "").
                  append(ScType.presentableText(param.tp)).append(", ")
        }
      }
      builder.delete(builder.length - 2, builder.length)
      builder.append(")")
    }
    if (settings.calcReturnTypeIsUnit) {
        builder.append("{\n")
    } else {
        builder.append(": ")
        builder.append(settings.calcReturnTypeText)
        builder.append(" = {\n")
    }
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
    val elementsToAdd: Iterator[PsiElement] = settings.elements.toSeq match {
      case Seq(x: ScBlockExpr) => x.children.toSeq.drop(1).dropRight(1).toIterator // drop '{' and '}'
      case x => x.toIterator
    }
    for (element <- elementsToAdd) {
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
    val method = ScalaPsiElementFactory.createMethodFromText(builder.toString(), settings.elements.apply(0).getManager)


    val returnVisitor = new ScalaRecursiveElementVisitor {
      override def visitReturnStatement(ret: ScReturnStmt) {
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
        val retElem = ScalaPsiElementFactory.createExpressionFromText(retText.toString(), ret.getManager)
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
      override def visitReference(ref: ScReferenceElement) {
        ref.bind() match {
          case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) =>{
            if (named.getContainingFile == method.getContainingFile && named.getTextOffset < offset &&
                    !named.name.startsWith("_")) {
              val oldName = named.name
              var break = false
              for (param <- settings.parameters if !break) {
                if (param.oldName == oldName) {
                  def tail() {
                    if (param.oldName != param.newName) {
                      val newRef = ScalaPsiElementFactory.createExpressionFromText(param.newName, method.getManager)
                      ref.getParent.getNode.replaceChild(ref.getNode, newRef.getNode)
                    }
                  }
                  ref.getParent match {
                    case sect: ScUnderscoreSection if param.isFunction => {
                      val newRef = ScalaPsiElementFactory.createExpressionFromText(param.newName, method.getManager)
                      sect.getParent.getNode.replaceChild(sect.getNode, newRef.getNode)
                    }
                    case _ if param.isEmptyParamFunction => {
                      ref.getParent match {
                        case ref: ScReferenceElement if ref.refName == "apply" => tail()
                        case call: ScMethodCall => tail()
                        case _ => {
                          ref.asInstanceOf[ScExpression].expectedType() match {
                            case Some(ScFunctionType(_, params)) if params.length == 0 => tail()
                            case _ => {
                              //we need to replace by method call
                              val newRef = ScalaPsiElementFactory.createExpressionFromText(param.newName + "()", method.getManager)
                              ref.getParent.getNode.replaceChild(ref.getNode, newRef.getNode)
                            }
                          }
                        }
                      }
                    }
                    case _ => {
                      tail()
                    }
                  }
                  break = true
                }
              }
            }
          }
          case _ =>
        }
        super.visitReference(ref)
      }
    }
    visitor.visitElement(method)

    val bindTo = new ArrayBuffer[(PsiNamedElement, String)]
    val newVisitor = new ScalaRecursiveElementVisitor() {
      override def visitElement(element: ScalaPsiElement) {
        element match {
          case named: PsiNamedElement if named != method && named.getTextOffset < offset => {
            var break = false
            for (param <- settings.parameters if !break) {
              if (param.oldName == named.name) {
                if (param.oldName != param.newName) {
                  bindTo += ((named, param.newName))
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

  class ScalaVariableData(val vari: ScTypedDefinition, val isMutable: Boolean, val isInsideOfElements: Boolean, 
                          val tp: ScType, val param: FakePsiParameter)
          extends ParameterTablePanel.VariableData(param, new FakePsiType(tp)) {
    passAsParameter = true
    name = param.name
  }

  @Nullable
  def convertVariableData(variable: VariableInfo, elements: Array[PsiElement]): ParameterTablePanel.VariableData = {
    var isMutable = false
    if (!variable.element.isInstanceOf[ScTypedDefinition]) return null
    val definition = variable.element.asInstanceOf[ScTypedDefinition]

    def checkIsMutable(ref: ScReferenceElement) {
      if (ScalaPsiUtil.isLValue(ref)) {
        ref.bind() match {
          case Some(ScalaResolveResult(elem, _)) if elem == definition => isMutable = true
          case _ =>
        }
      }
    }

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        checkIsMutable(ref)
        super.visitReference(ref)
      }
    }
    for (element <- elements if element.isInstanceOf[ScalaPsiElement] && !isMutable) {
      element match {
        case spe: ScalaPsiElement => spe.accept(visitor: ScalaElementVisitor)
      }
    }
    val isInside = if (elements.length > 0) {
      val startOffset = elements(0).getTextRange.getStartOffset
      val endOffset = elements(elements.length - 1).getTextRange.getEndOffset
      definition.getTextOffset >= startOffset && definition.getTextOffset < endOffset
    } else false
    val retType = definition.getType(TypingContext.empty).getOrNothing
    val tp = definition match {
      case fun: ScFunction if fun.paramClauses.clauses.length == 0 =>
        new ScFunctionType(retType, Seq.empty)(definition.getProject, definition.getResolveScope)
      case _ => retType
    }
    val param = new FakePsiParameter(definition.getManager, ScalaFileType.SCALA_LANGUAGE, new Parameter("", tp, false, false, false, -1), definition.name)
    new ScalaVariableData(definition, isMutable, isInside, tp, param)
  }

  /**
   * @return returnTypePresentableText
   */
  def calcReturnType(returnType: Option[ScType], returns: Array[ExtractMethodReturn], lastReturn: Boolean,
                     lastMeaningful: Option[ScType]): String = calcReturnTypeExt(returnType, returns, lastReturn, lastMeaningful)._2

  /**
   * @return (isUnit, returnTypePresentableText)
   */
  def calcReturnTypeExt(returnType: Option[ScType], returns: Array[ExtractMethodReturn], lastReturn: Boolean,
                     lastMeaningful: Option[ScType]): (Boolean, String) = {
    def prepareResult(t: ScType) = {
      val isUnit = t == Unit
      (isUnit, ScType.presentableText(t))
    }
    if (lastReturn) {
      return prepareResult(returnType.get)
    }
    if (returns.length == 0 && returnType == None && lastMeaningful != None) {
      return prepareResult(lastMeaningful.get)
    }
    returnType match {
      case Some(psi.types.Unit) => {
        if (returns.length == 0) (false, "Boolean")
        else if (returns.length == 1) (false, "(Boolean, Option[" + ScType.presentableText(returns.apply(0).returnType) + "])")
        else (false, "(Boolean, Option[" + returns.map(r => ScType.presentableText(r.returnType)).mkString("(", ", ", ")") + "])")
      }
      case Some(tp) => {
        if (returns.length == 0) (false, "Option[" + ScType.presentableText(tp) + "]")
        else if (returns.length == 1) (false, "(Option[" + ScType.presentableText(tp) +
                "], Option[" + ScType.presentableText(returns.apply(0).returnType) + "])")
        else (false, "(Option[" + ScType.presentableText(tp) +"]," +
                " Option[" + returns.map(r => ScType.presentableText(r.returnType)).mkString("(", ", ", ")") + "])")
      }
      case None => {
        if (returns.length == 1) prepareResult(returns.apply(0).returnType)
        else if (returns.length == 0) (true, "Unit")
        else (false, returns.map(r => ScType.presentableText(r.returnType)).mkString("(", ", ", ")"))
      }
    }
  }

  def getParameter(d: VariableData, variableData: ScalaVariableData): ExtractMethodParameter = {
    new ExtractMethodParameter(d.variable.name, d.name, false, (d.`type`.asInstanceOf[FakePsiType]).tp,
        variableData.isMutable, d.passAsParameter, variableData.vari.isInstanceOf[ScFunction],
        variableData.vari.isInstanceOf[ScFunction] && variableData.vari.asInstanceOf[ScFunction].parameters.length == 0,
        ScalaPsiUtil.nameContext(variableData.vari) match {
          case v: ScValue if v.hasModifierProperty("lazy") => true
          case p: ScParameter if p.isCallByNameParameter => true
          case _ => false
        })
  }

  /**
   * methods for Unit tests
   */
  def getParameters(myInput: Array[VariableInfo], elements: Array[PsiElement]) = {
    var buffer: ArrayBuffer[VariableData] = new ArrayBuffer[VariableData]
    for (input <- myInput) {
      var d: VariableData = ScalaExtractMethodUtils.convertVariableData(input, elements)
      if (d != null) buffer += d
    }
    val data = buffer.toArray
    var list: ArrayBuffer[ExtractMethodParameter] = new ArrayBuffer[ExtractMethodParameter]
    for (d <- data) {
      val variableData: ScalaVariableData = d.asInstanceOf[ScalaVariableData]
      var param: ExtractMethodParameter = getParameter(d, variableData)
      list += param
    }
    val res = list.toArray
    Sorting.stableSort[ExtractMethodParameter](res, (p1: ExtractMethodParameter, p2: ExtractMethodParameter) => {p1.oldName < p2.oldName})
    res
  }

  def getReturns(myOutput: Array[VariableInfo], elements: Array[PsiElement]): Array[ExtractMethodReturn] = {
    val list: ArrayList[ExtractMethodReturn] = new ArrayList[ExtractMethodReturn]
    for (info <- myOutput) {
      val data: ScalaVariableData = ScalaExtractMethodUtils.convertVariableData(info, elements).asInstanceOf[ScalaVariableData]
      val tp: FakePsiType = data.`type`.asInstanceOf[FakePsiType]
      val aReturn: ExtractMethodReturn = new ExtractMethodReturn(info.element.name, tp.tp, data.isInsideOfElements,
        ScalaPsiUtil.nameContext(info.element).isInstanceOf[ScValue] ||
          ScalaPsiUtil.nameContext(info.element).isInstanceOf[ScFunction])
      list.add(aReturn)
    }
    list.toArray(new Array[ExtractMethodReturn](list.size))
  }
}