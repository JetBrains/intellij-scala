package org.jetbrains.plugins.scala
package lang.refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ExtractMethodParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScFunction}
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import com.intellij.psi.{PsiAnnotation, PsiPrimitiveType, PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypedDefinition, ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScFunctionType, ScSubstitutor, Unit}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import com.intellij.refactoring.util.VariableData
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import scala.util.Sorting
import java.util
import extensions._
import org.jetbrains.plugins.scala.lang.refactoring.util.duplicates.ScalaVariableData

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

object ScalaExtractMethodUtils {
  def createMethodFromSettings(settings: ScalaExtractMethodSettings): ScFunction = {
    val accessMod = settings.visibility
    val methodName = settings.methodName
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
    val typeParamsText = if (tp.length != 0) tp.reverse.map(_.getText).mkString("[", ", ", "]") else ""

    def paramText(param: ExtractMethodParameter): String = {
      val name = if (!param.needMirror) param.oldName else s"_${param.newName}"
      val colon = if (ScalaNamesUtil.isOpCharacter(name.last)) " : " else ": "
      val byNameArrow = if (param.isCallByNameParameter) "=> " else ""
      val typeText = param.tp.presentableText
      s"$name$colon$byNameArrow$typeText"
    }

    val parameters = settings.parameters.filter(_.passAsParameter).map(paramText)
    val paramsText = if (parameters.nonEmpty) parameters.mkString("(", ", ",")") else ""

    val retType = if (settings.calcReturnTypeIsUnit) "" else s": ${settings.calcReturnTypeText} ="

    def notPassedOrWithMirrorParamText(param: ExtractMethodParameter) = {
      val keyword = if (param.needMirror) "var " else "val "
      val name = param.oldName
      val colon = if (ScalaNamesUtil.isOpCharacter(name.last)) " : " else ": "
      val typeText = param.tp.presentableText
      val expr = if (!param.passAsParameter) "_" else if (param.needMirror) s"_${param.newName}"
      s"$keyword$name$colon$typeText = $expr\n"
    }
    val notPassedParams = settings.parameters.filter(p => !p.passAsParameter || p.needMirror).map(notPassedOrWithMirrorParamText)
    val notPassedParamsText = notPassedParams.mkString

    val elementsToAdd: Iterator[PsiElement] = settings.elements.toSeq match {
      case Seq(x: ScBlockExpr) => x.children.toSeq.drop(1).dropRight(1).toIterator // drop '{' and '}'
      case x => x.toIterator
    }
    val elementsText = elementsToAdd.map(_.getText).mkString("")

    val returnText =
      if (settings.lastReturn) ""
      else {
        def params = settings.returns.map(_.oldParamName).mkString("(", ", ", ")")
        def byReturnsSize(ifZero: => String, ifOne: => String, ifMany: => String): String = {
          settings.returns.length match {
            case 0 => ifZero
            case 1 => ifOne
            case _ => ifMany
          }
        }
        settings.returnType match {
          case Some(psi.types.Unit) => byReturnsSize("\nfalse", s"\n(false, Some$params)", s"\n(false, Some($params))")
          case Some(_) => byReturnsSize("\nNone", s"\n(None, Some$params)", s"\n(false, Some($params))")
          case _ => byReturnsSize("", s"\n${settings.returns(0).oldParamName}", s"\n$params")
        }
      }

    val firstPart = s"${accessMod}def $methodName$typeParamsText$paramsText$retType {\n$notPassedParamsText"
    val offset = firstPart.length
    val secondPart = s"$elementsText$returnText\n}"
    val method = ScalaPsiElementFactory.createMethodFromText(firstPart + secondPart, settings.elements.apply(0).getManager)

    val returnVisitor = new ScalaRecursiveElementVisitor {
      override def visitReturnStatement(ret: ScReturnStmt) {
        if (ret.returnFunction != Some(method)) return
        val text = ret.expr match {
          case Some(t) if !settings.lastReturn => s"Some(${t.getText})"
          case Some(t) => t.getText
          case _ if !settings.lastReturn => "true"
          case _ => ""
        }
        val retText =
          if (settings.returns.length == 0 || settings.lastReturn) s"return $text"
          else s"return ($text, None)"
        val retElem = ScalaPsiElementFactory.createExpressionFromText(retText, ret.getManager)
        ret.replace(retElem)
        super.visitReturnStatement(ret)
      }
    }
    returnVisitor.visitElement(method: ScalaPsiElement)

    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReference(ref: ScReferenceElement) {
        ref.bind() match {
          case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) =>
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
                    case sect: ScUnderscoreSection if param.isFunction =>
                      val newRef = ScalaPsiElementFactory.createExpressionFromText(param.newName, method.getManager)
                      sect.getParent.getNode.replaceChild(sect.getNode, newRef.getNode)
                    case _ if param.isEmptyParamFunction =>
                      ref.getParent match {
                        case ref: ScReferenceElement if ref.refName == "apply" => tail()
                        case call: ScMethodCall => tail()
                        case _ =>
                          ref.asInstanceOf[ScExpression].expectedType() match {
                            case Some(ScFunctionType(_, params)) if params.length == 0 => tail()
                            case _ =>
                              //we need to replace by method call
                              val newRef = ScalaPsiElementFactory.createExpressionFromText(param.newName + "()", method.getManager)
                              ref.getParent.getNode.replaceChild(ref.getNode, newRef.getNode)
                          }
                      }
                    case _ => tail()
                  }
                  break = true
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
          case named: PsiNamedElement if named != method && named.getTextOffset < offset =>
            settings.parameters.find(p => p.oldName == named.name)
                    .filter(p => p.oldName != p.newName)
                    .foreach(p => bindTo += ((named, p.newName)))
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

  @Nullable
  def convertVariableData(variable: VariableInfo, elements: Array[PsiElement]): ScalaVariableData = {
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
        ScFunctionType(retType, Seq.empty)(definition.getProject, definition.getResolveScope)
      case _ => retType
    }
    new ScalaVariableData(definition, isMutable, isInside, tp)
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
    def byReturnsSize[T](ifZero: => T, ifOne: => T, ifMany: => T): T = {
      returns.length match {
        case 0 => ifZero
        case 1 => ifOne
        case _ => ifMany
      }
    }
    def tupleTypeText = returns.map(_.returnType.presentableText).mkString("(", ", ", ")")
    def typeText = returns(0).returnType.presentableText
    returnType match {
      case Some(psi.types.Unit) => byReturnsSize(
        (false, "Boolean"),
        (false, s"(Boolean, Option[$typeText])"),
        (false, s"(Boolean, Option[$tupleTypeText])")
      )
      case Some(tp) => byReturnsSize(
        (false, s"Option[${tp.presentableText}]"),
        (false, s"(Option[${tp.presentableText}], Option[$typeText])"),
        (false, s"(Option[${tp.presentableText}], Option[$tupleTypeText])")
      )
      case None => byReturnsSize(
        (true, "Unit"),
        prepareResult(returns(0).returnType),
        (false, tupleTypeText)
      )
    }
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
      list += ExtractMethodParameter.from(d.asInstanceOf[ScalaVariableData])
    }
    val res = list.toArray
    Sorting.stableSort[ExtractMethodParameter](res, (p1: ExtractMethodParameter, p2: ExtractMethodParameter) => {p1.oldName < p2.oldName})
    res
  }

  def getReturns(myOutput: Array[VariableInfo], elements: Array[PsiElement]): Array[ExtractMethodReturn] = {
    val list: util.ArrayList[ExtractMethodReturn] = new util.ArrayList[ExtractMethodReturn]
    for (info <- myOutput) {
      val data: ScalaVariableData = ScalaExtractMethodUtils.convertVariableData(info, elements)
      list.add(ExtractMethodReturn.from(data))
    }
    list.toArray(new Array[ExtractMethodReturn](list.size))
  }
}
