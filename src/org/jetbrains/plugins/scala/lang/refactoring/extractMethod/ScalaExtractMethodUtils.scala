package org.jetbrains.plugins.scala
package lang.refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import com.intellij.psi.{PsiNamedElement, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypedDefinition, ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScFunctionType, ScSubstitutor, Unit}
import com.intellij.refactoring.util.VariableData
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import scala.util.Sorting
import java.util
import extensions._
import org.jetbrains.plugins.scala.lang.refactoring.util.duplicates.ScalaVariableData
import com.intellij.openapi.util.text.StringUtil

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

    def paramText(param: ExtractMethodParameter): String = typedName(param.oldName, param.tp.canonicalText, param.isCallByNameParameter)

    val parameters = settings.parameters.filter(_.passAsParameter).map(paramText)
    val paramsText = if (parameters.nonEmpty) parameters.mkString("(", ", ",")") else ""

    val retType = if (settings.calcReturnTypeIsUnit) "" else s": ${settings.calcReturnTypeText} ="

    val notPassedParams = settings.parameters.filter(p => !p.passAsParameter).map { p =>
      val nameAndType = typedName(p.oldName, p.tp.canonicalText)
      s"val $nameAndType = _\n"
    }
    val notPassedParamsText = notPassedParams.mkString

    val elementsToAdd: Iterator[PsiElement] = settings.elements.toSeq match {
      case Seq(x: ScBlockExpr) => x.children.toSeq.drop(1).dropRight(1).toIterator // drop '{' and '}'
      case x => x.toIterator
    }
    val elementsText = elementsToAdd.map(_.getText).mkString("")

    def byOutputsSize(ifZero: => String, ifOne: => String, ifMany: => String): String = {
      settings.outputs.length match {
        case 0 => ifZero
        case 1 => ifOne
        case _ => ifMany
      }
    }

    val returnText =
      if (settings.lastReturn) ""
      else {
        def params = settings.outputs.map(_.paramName).mkString("(", ", ", ")")
        def multipleReturnText = {
          val ics = settings.innerClassSettings
          if (!ics.needClass) params //tuple
          else if (ics.isCase) s"${ics.className}$params"
          else s"new ${ics.className}$params"
        }

        settings.returnType match {
          case Some(psi.types.Unit) => byOutputsSize(
            "\nfalse",
            s"\nSome$params",
            s"\nSome($multipleReturnText)")
          case Some(_) => byOutputsSize(
            "\nNone",
            s"\nRight$params",
            s"\nRight($multipleReturnText)")
          case _ => byOutputsSize(
            "",
            s"\n${settings.outputs(0).paramName}",
            s"\n$multipleReturnText")
        }
      }

    val firstPart = s"${accessMod}def $methodName$typeParamsText$paramsText$retType {\n$notPassedParamsText"
    val offset = firstPart.length
    val secondPart = s"$elementsText$returnText\n}"
    val method = ScalaPsiElementFactory.createMethodFromText(firstPart + secondPart, settings.elements.apply(0).getManager)

    val returnVisitor = new ScalaRecursiveElementVisitor {
      override def visitReturnStatement(ret: ScReturnStmt) {
        if (ret.returnFunction != Some(method)) return
        val retExprText = ret.expr.map(_.getText).mkString
        val newText = settings.returnType match {
          case Some(psi.types.Unit) => byOutputsSize(
            "true",
            "None",
            "None"
          )
          case Some(_) => byOutputsSize(
            s"Some($retExprText)",
            s"Left($retExprText)",
            s"Left($retExprText)"
          )
          case None => "" //should not occur
        }
        val retElem = ScalaPsiElementFactory.createExpressionFromText(s"return $newText", ret.getManager)
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
    if (!variable.element.isInstanceOf[ScTypedDefinition]) return null
    val definition = variable.element.asInstanceOf[ScTypedDefinition]

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
    new ScalaVariableData(definition, isInside, tp)
  }

  /**
   * @return returnTypePresentableText
   */
  def calcReturnType(settings: ScalaExtractMethodSettings): String = calcReturnTypeExt(settings)._2

  /**
   * @return (isUnit, returnTypePresentableText)
   */
  def calcReturnTypeExt(settings: ScalaExtractMethodSettings): (Boolean, String) = {
    def prepareResult(t: ScType) = {
      val isUnit = t == Unit
      (isUnit, ScType.presentableText(t))
    }
    val returnStmtType = settings.returnType
    val outputs = settings.outputs
    val lastMeaningful = settings.lastMeaningful
    if (settings.lastReturn) {
      return prepareResult(returnStmtType.get)
    }
    if (outputs.length == 0 && returnStmtType == None && lastMeaningful != None) {
      return prepareResult(lastMeaningful.get)
    }
    def byOutputsSize[T](ifZero: => T, ifOne: => T, ifMany: => T): T = {
      outputs.length match {
        case 0 => ifZero
        case 1 => ifOne
        case _ => ifMany
      }
    }
    val outputType = outputTypeText(settings)
    returnStmtType match {
      case Some(psi.types.Unit) => byOutputsSize(
        (false, "Boolean"),
        (false, s"Option[$outputType]"),
        (false, s"Option[$outputType]")
      )
      case Some(tp) => byOutputsSize(
        (false, s"Option[${tp.presentableText}]"),
        (false, s"Either[${tp.presentableText}, $outputType]"),
        (false, s"Either[${tp.presentableText}, $outputType]")
      )
      case None => byOutputsSize(
        (true, "Unit"),
        prepareResult(outputs(0).returnType),
        (false, outputType)
      )
    }
  }

  def outputTypeText(settings: ScalaExtractMethodSettings) = {
    if (settings.innerClassSettings.needClass) settings.innerClassSettings.className
    else {
      val outputs = settings.outputs
      outputs.length match {
        case 0 => ""
        case 1 => outputs(0).returnType.presentableText
        case _ => outputs.map(_.returnType.presentableText).mkString("(", ", ", ")")
      }
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

  def getReturns(myOutput: Array[VariableInfo], elements: Array[PsiElement]): Array[ExtractMethodOutput] = {
    val list: util.ArrayList[ExtractMethodOutput] = new util.ArrayList[ExtractMethodOutput]
    for (info <- myOutput) {
      val data: ScalaVariableData = ScalaExtractMethodUtils.convertVariableData(info, elements)
      list.add(ExtractMethodOutput.from(data))
    }
    list.toArray(new Array[ExtractMethodOutput](list.size))
  }

  def typedName(name: String, typeText: String, byName: Boolean = false): String = {
    val colon = if (ScalaNamesUtil.isOpCharacter(name.last)) " : " else ": "
    val byNameArrow = if (byName) "=> " else ""
    s"$name$colon$byNameArrow$typeText"
  }

  def previewSignatureText(settings: ScalaExtractMethodSettings) = {
    def nameAndType(param: ExtractMethodParameter): String =
      this.typedName(param.newName, param.tp.presentableText, param.isCallByNameParameter)

    val ics = settings.innerClassSettings
    val classText = if (ics.needClass) s"${ics.classText(false)}\n\n" else ""

    val methodName = settings.methodName
    val visibility = settings.visibility
    val prefix = s"${visibility}def $methodName"
    val paramsText = settings.parameters
            .filter(_.passAsParameter)
            .map(p => nameAndType(p))
            .mkString("(", s", ", ")")
    val returnTypeText = calcReturnType(settings)
    s"$classText$prefix$paramsText: $returnTypeText"
  }
}
