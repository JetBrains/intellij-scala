package org.jetbrains.plugins.scala
package lang.refactoring.extractMethod

import java.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveState}
import com.intellij.refactoring.util.VariableData
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScSubstitutor, ScType, Unit}
import org.jetbrains.plugins.scala.lang.psi.{TypeAdjuster, ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.duplicates.DuplicateMatch
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Sorting

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

object ScalaExtractMethodUtils {
  def createMethodFromSettings(settings: ScalaExtractMethodSettings): ScFunction = {
    val accessMod = settings.visibility
    val methodName = settings.methodName

    val typeParamsText = typeParametersText(settings)

    def paramText(param: ExtractMethodParameter): String =
      typedName(param.oldName, param.tp.canonicalText, param.fromElement.getProject, param.isCallByNameParameter)

    val parameters = settings.parameters.filter(_.passAsParameter).map(paramText)
    val paramsText = if (parameters.nonEmpty) parameters.mkString("(", ", ",")") else ""

    val project = settings.elements(0).getProject
    val codeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
    val retType =
      if (settings.calcReturnTypeIsUnit && !codeStyleSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT) ""
      else s": ${settings.calcReturnTypeText} ="

    val notPassedParams = settings.parameters.filter(p => !p.passAsParameter).map { p =>
      val nameAndType = typedName(p.oldName, p.tp.canonicalText, p.fromElement.getProject)
      s"val $nameAndType = ???\n"
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

    if (!settings.lastReturn) {
      val returnVisitor = new ScalaRecursiveElementVisitor {
        override def visitReturnStatement(ret: ScReturnStmt) {
          if (!ret.returnFunction.contains(method)) return
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
        }
      }
      returnVisitor.visitElement(method: ScalaPsiElement)
    }

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
                            case Some(ScFunctionType(_, params)) if params.isEmpty => tail()
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
      case fun: ScFunction if fun.paramClauses.clauses.isEmpty =>
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
    val lastExprType = settings.lastExprType
    if (settings.lastReturn) {
      return prepareResult(returnStmtType.get)
    }
    if (outputs.length == 0 && returnStmtType.isEmpty && lastExprType.isDefined) {
      return prepareResult(lastExprType.get)
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

  def typedName(name: String, typeText: String, project: Project, byName: Boolean = false): String = {
    val colon = if (StringUtil.isEmpty(name) || ScalaNamesUtil.isOpCharacter(name.last)) " : " else ": "
    val arrow = ScalaPsiUtil.functionArrow(project) + " "
    val byNameArrow = if (byName) arrow else ""
    s"$name$colon$byNameArrow$typeText"
  }

  def previewSignatureText(settings: ScalaExtractMethodSettings) = {
    def nameAndType(param: ExtractMethodParameter): String =
      this.typedName(param.newName, param.tp.presentableText, param.fromElement.getProject, param.isCallByNameParameter)

    val ics = settings.innerClassSettings
    val classText = if (ics.needClass) s"${ics.classText(canonTextForTypes = false)}\n\n" else ""

    val methodName = settings.methodName
    val visibility = settings.visibility
    val prefix = s"${visibility}def $methodName"
    val typeParamsText = typeParametersText(settings)
    val paramsText = settings.parameters
            .filter(_.passAsParameter)
            .map(p => nameAndType(p))
            .mkString("(", s", ", ")")
    val returnTypeText = calcReturnType(settings)
    s"$classText$prefix$typeParamsText$paramsText: $returnTypeText"
  }

  def replaceWithMethodCall(settings: ScalaExtractMethodSettings, d: DuplicateMatch)
                           (implicit typeSystem: TypeSystem = d.typeSystem) {
    replaceWithMethodCall(settings, d.candidates, d.parameterText, d.outputName)
  }

  def replaceWithMethodCall(settings: ScalaExtractMethodSettings,
                            elements: Seq[PsiElement],
                            parameterText: ExtractMethodParameter => String,
                            outputName: ExtractMethodOutput => String)
                           (implicit typeSystem: TypeSystem) {
    val element = elements.find(elem => elem.isInstanceOf[ScalaPsiElement]).getOrElse(return)
    val manager = element.getManager
    val processor = new CompletionProcessor(StdKinds.refExprLastRef, element, includePrefixImports = false)
    PsiTreeUtil.treeWalkUp(processor, element, null, ResolveState.initial)
    val allNames = new mutable.HashSet[String]()
    allNames ++= processor.candidatesS.map(rr => rr.element.name)
    def generateFreshName(s: String): String = {
      var freshName = s
      var count = 0
      while (allNames.contains(freshName)) {
        count += 1
        freshName = s + count
      }
      freshName
    }
    val mFreshName = generateFreshName(settings.methodName + "Result")

    val params = settings.parameters.filter(_.passAsParameter)
            .map(param => parameterText(param) + (if (param.isFunction) " _" else ""))

    val paramsText = if (params.nonEmpty) params.mkString("(", ", ", ")") else ""
    val methodCallText = s"${settings.methodName}$paramsText"
    var needExtractorsFromMultipleReturn = false

    val outputTypedNames = settings.outputs.map(o =>
      ScalaExtractMethodUtils.typedName(outputName(o), o.returnType.canonicalText, o.fromElement.getProject))
    val ics = settings.innerClassSettings

    def patternForDeclaration: String = {
      if (ics.needClass) return s"$mFreshName: ${ics.className}"

      if (outputTypedNames.length == 0) ""
      else if (outputTypedNames.length == 1) outputTypedNames(0)
      else outputTypedNames.mkString("(", ", ", ")")
    }

    def insertCallStmt(): PsiElement = {
      def insertExpression(text: String): PsiElement = {
        val expr = ScalaPsiElementFactory.createExpressionFromText(text, manager)
        elements.head.replace(expr)
      }
      if (settings.lastReturn) insertExpression(s"return $methodCallText")
      else if (settings.outputs.length == 0) {
        val exprText = settings.returnType match {
          case None => methodCallText
          case Some(psi.types.Unit) => s"if ($methodCallText) return"
          case Some(_) =>
            val arrow = ScalaPsiUtil.functionArrow(manager.getProject)
            s"""$methodCallText match {
                 |  case Some(toReturn) $arrow return toReturn
                 |  case None $arrow
                 |}""".stripMargin.replace("\r", "")
        }
        insertExpression(exprText)
      }
      else {
        val (pattern, isVal) = settings.outputs match {
          case _ if ics.needClass =>
            needExtractorsFromMultipleReturn = true
            (patternForDeclaration, true)
          case outputs if outputs.forall(_.isVal) =>
            (patternForDeclaration, true)
          case outputs if outputs.forall(!_.isVal) =>
            (patternForDeclaration, false)
          case outputs =>
            needExtractorsFromMultipleReturn = true
            val typeText = ScalaExtractMethodUtils.outputTypeText(settings)
            (s"$mFreshName: $typeText", true)
        }
        val arrow = ScalaPsiUtil.functionArrow(manager.getProject)
        val exprText = settings.returnType match {
          case None => methodCallText
          case Some(psi.types.Unit) =>
            s"""$methodCallText match {
                  |  case Some(result) $arrow result
                  |  case None $arrow return
                  |}""".stripMargin.replace("\r", "")
          case Some(_) =>
            s"""$methodCallText match {
                  |  case Left(toReturn) $arrow return toReturn
                  |  case Right(result) $arrow result
                  |}""".stripMargin.replace("\r", "")
        }
        val expr = ScalaPsiElementFactory.createExpressionFromText(exprText, manager)
        val declaration = ScalaPsiElementFactory.createDeclaration(pattern, "", isVariable = !isVal, expr, manager)
        val result = elements.head.replace(declaration)
        TypeAdjuster.markToAdjust(result)
        result
      }
    }

    def insertAssignsFromMultipleReturn(element: PsiElement){
      if (!needExtractorsFromMultipleReturn) return

      var lastElem: PsiElement = element
      def addElement(elem: PsiElement) = {
        lastElem = lastElem.getParent.addAfter(elem, lastElem)
        lastElem.getParent.addBefore(ScalaPsiElementFactory.createNewLine(elem.getManager), lastElem)
        lastElem
      }

      def addAssignment(ret: ExtractMethodOutput, extrText: String) {
        val stmt =
          if (ret.needNewDefinition)
            ScalaPsiElementFactory.createDeclaration(ret.returnType, ret.paramName, !ret.isVal, extrText, manager, isPresentableText = false)
          else
            ScalaPsiElementFactory.createExpressionFromText(ret.paramName + " = " + extrText, manager)

        addElement(stmt)
      }

      val allVals = settings.outputs.forall(_.isVal)
      val allVars = settings.outputs.forall(!_.isVal)

      def addExtractorsFromCaseClass() {
        if (allVals || allVars) {
          val patternArgsText = outputTypedNames.mkString("(", ", ", ")")
          val patternText = ics.className + patternArgsText
          val expr = ScalaPsiElementFactory.createExpressionFromText(mFreshName, manager)
          val stmt = ScalaPsiElementFactory.createDeclaration(patternText, "", isVariable = allVars, expr, manager)
          addElement(stmt)
        } else {
          addExtractorsFromClass()
        }
      }

      def addExtractorsFromClass() {
        for (ret <- settings.outputs) {
          val exprText = s"$mFreshName.${ret.paramName}"
          addAssignment(ret, exprText)
        }
      }

      if (!ics.needClass) {
        var count = 1
        for (ret <- settings.outputs) {
          val exprText = s"$mFreshName._$count"
          addAssignment(ret, exprText)
          count += 1
        }
      }
      else if (ics.isCase) addExtractorsFromCaseClass()
      else addExtractorsFromClass()
    }

    def removeReplacedElements() {
      val valid = elements.dropWhile(!_.isValid)
      if (valid.isEmpty) return

      valid.head.getParent.deleteChildRange(valid.head, valid.last)
    }

    val stmt = insertCallStmt()
    insertAssignsFromMultipleReturn(stmt)
    removeReplacedElements()
    TypeAdjuster.markToAdjust(stmt.getParent)
  }

  private def typeParametersText(settings: ScalaExtractMethodSettings) =
    settings.typeParameters match {
      case Seq() => ""
      case seq => seq.map(_.getText).mkString("[", ", ", "]")
    }
}
