package org.jetbrains.plugins.scala
package lang
package parameterInfo

import java.awt.Color

import _root_.org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.lookup.{LookupElement, LookupItem}
import com.intellij.lang.parameterInfo._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.hash.HashSet
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parameterInfo.ScalaFunctionParameterInfoHandler.AnnotationParameters
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

import _root_.scala.collection.mutable.ArrayBuffer
import scala.annotation.tailrec
import scala.collection.Seq

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.01.2009
 */

class ScalaFunctionParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[PsiElement, Any, ScExpression] {
  def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod])
  }

  def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(elem: PsiElement): Array[ScExpression] = {
    elem match {
      case argExprList: ScArgumentExprList =>
        argExprList.exprs.toArray
      case u: ScUnitExpr => Array.empty
      case p: ScParenthesisedExpr => p.expr.toArray
      case t: ScTuple => t.exprs.toArray
      case e: ScExpression => Array(e)
      case _ => Array.empty
    }
  }

  def getArgumentListClass: Class[PsiElement] = classOf[PsiElement]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: java.util.Set[Class[_]] = {
    val set = new HashSet[Class[_]]()
    set.add(classOf[ScMethodCall])
    set.add(classOf[ScConstructor])
    set.add(classOf[ScSelfInvocation])
    set.add(classOf[ScInfixExpr])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement = {
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement = {
    findCall(context)
  }

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = {
    p match {
      case x: ScFunction =>
        x.parameters.toArray
      case _ => ArrayUtil.EMPTY_OBJECT_ARRAY
    }
  }

  def showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = {
    if (!item.isInstanceOf[LookupItem[_]]) return null
    val allElements = JavaCompletionUtil.getAllPsiElements(item.asInstanceOf[LookupItem[_]])

    if (allElements != null &&
        allElements.size > 0 &&
        allElements.get(0).isInstanceOf[PsiMethod]) {
      return allElements.toArray(new Array[Object](allElements.size))
    }
    null
  }

  def updateParameterInfo(o: PsiElement, context: UpdateParameterInfoContext) {
    if (context.getParameterOwner != o) context.removeHint()
    val offset = context.getOffset
    var child = o.getNode.getFirstChildNode
    var i = 0
    while (child != null && child.getStartOffset < offset) {
      if (child.getElementType == ScalaTokenTypes.tCOMMA) i = i + 1
      child = child.getTreeNext
    }
    context.setCurrentParameter(i)
  }


  def updateUI(p: Any, context: ParameterInfoUIContext) {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: PsiElement =>
        var color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        var isGrey = false
        //todo: var isGreen = true
        var namedMode = false
        def paramText(param: ScParameter, subst: ScSubstitutor, implicitParamType: Option[ScType] = None) = {
          ScalaDocumentationProvider.parseParameter(param,
            (t: ScType) =>
              ScType.presentableText(subst.subst(t)), escape = false, implicitParamType)
        }
        def applyToParameters(parameters: Seq[(Parameter, String)], subst: ScSubstitutor, canBeNaming: Boolean,
                              isImplicit: Boolean = false) {
          if (parameters.length > 0) {
            var k = 0
            val exprs: Seq[ScExpression] = getActualParameters(args)
            if (isImplicit) buffer.append("implicit ")
            val used = new Array[Boolean](parameters.length)
            while (k < parameters.length) {
              val namedPrefix = "["
              val namedPostfix = "]"

              def appendFirst(useGrey: Boolean = false) {
                val getIt = used.indexOf(false)
                used(getIt) = true
                if (namedMode) buffer.append(namedPrefix)
                val param: (Parameter, String) = parameters(getIt)

                buffer.append(param._2)
                if (namedMode) buffer.append(namedPostfix)
              }
              def doNoNamed(expr: ScExpression) {
                if (namedMode) {
                  isGrey = true
                  appendFirst()
                } else {
                  val exprType = expr.getType(TypingContext.empty).getOrNothing
                  val getIt = used.indexOf(false)
                  used(getIt) = true
                  val param: (Parameter, String) = parameters(getIt)
                  val paramType = param._1.paramType
                  if (!exprType.conforms(paramType)) isGrey = true
                  buffer.append(param._2)
                }
              }
              if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
                      parameters(parameters.length - 1)._1.isRepeated)) {
                buffer.append("<b>")
              }
              if (k < index && !isGrey) {
                //slow checking
                if (k >= exprs.length) { //shouldn't be
                  appendFirst(useGrey = true)
                  isGrey = true
                } else {
                  exprs(k) match {
                    case assign@NamedAssignStmt(name) =>
                      val ind = parameters.indexWhere(param => ScalaPsiUtil.memberNamesEquals(param._1.name, name))
                      if (ind == -1 || used(ind)) {
                        doNoNamed(assign)
                      } else {
                        if (k != ind) namedMode = true
                        used(ind) = true
                        val param: (Parameter, String) = parameters(ind)
                        if (namedMode) buffer.append(namedPrefix)
                        buffer.append(param._2)
                        if (namedMode) buffer.append(namedPostfix)
                        assign.getRExpression match {
                          case Some(expr: ScExpression) =>
                            for (exprType <- expr.getType(TypingContext.empty)) {
                              val paramType = param._1.paramType
                              if (!exprType.conforms(paramType)) isGrey = true
                            }
                          case _ => isGrey = true
                        }
                      }
                    case expr: ScExpression =>
                      doNoNamed(expr)
                  }
                }
              } else {
                //fast checking
                if (k >= exprs.length) {
                  appendFirst()
                } else {
                  exprs(k) match {
                    case NamedAssignStmt(name) =>
                      val ind = parameters.indexWhere(param => ScalaPsiUtil.memberNamesEquals(param._1.name, name))
                      if (ind == -1 || used(ind)) {
                        appendFirst()
                      } else {
                        if (k != ind) namedMode = true
                        used(ind) = true
                        if (namedMode) buffer.append(namedPrefix)
                        buffer.append(parameters(ind)._2)
                        if (namedMode) buffer.append(namedPostfix)
                      }
                    case _ => appendFirst()
                  }
                }
              }
              if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
                      parameters(parameters.length - 1)._1.isRepeated)) {
                buffer.append("</b>")
              }
              k = k + 1
              if (k != parameters.length) buffer.append(", ")
            }
            if (!isGrey && exprs.length > parameters.length && index >= parameters.length) {
              if (!namedMode && parameters(parameters.length - 1)._1.isRepeated) {
                val paramType = parameters(parameters.length - 1)._1.paramType
                while (!isGrey && k < exprs.length.min(index)) {
                  if (k < index) {
                    for (exprType <- exprs(k).getType(TypingContext.empty)) {
                    if (!exprType.conforms(paramType)) isGrey = true
                    }
                  }
                  k = k + 1
                }
              } else isGrey = true
            }
          } else buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
        }
        p match {
          case x: String if x == "" =>
            buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
          case (a: AnnotationParameters, i: Int) =>
            val seq = a.seq
            if (seq.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val paramsSeq: Seq[(Parameter, String)] = seq.zipWithIndex.map {
                case (t, paramIndex) =>
                  (new Parameter(t._1, None, t._2, t._3 != null, false, false, paramIndex),
                    t._1 + ": " + ScType.presentableText(t._2) + (
                          if (t._3 != null) " = " + t._3.getText else ""))
              }
              applyToParameters(paramsSeq, ScSubstitutor.empty, canBeNaming = true, isImplicit = false)
            }
          case (sign: PhysicalSignature, i: Int, implicitParamType: ScType) =>
            val subst = sign.substitutor
            sign.method match {
              case method: ScFunction =>
                val clauses = method.effectiveParameterClauses
                if (clauses.length > i && i != -1) {
                  val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses(0)
                  val parameters: Seq[ScParameter] = clause.effectiveParameters
                  color = JBColor.PINK
                  applyToParameters(parameters.map(param =>
                    (new Parameter(param), paramText(param, subst, Some(implicitParamType)))),
                    subst, canBeNaming = true, isImplicit = clause.isImplicit)
                } else return
              case _ => return
            }
          case (sign: PhysicalSignature, i: Int) => //i  can be -1 (it's update method)
            val subst = sign.substitutor
            sign.method match {
              case method: ScFunction =>
                val clauses = method.effectiveParameterClauses
                if (clauses.length <= i || (i == -1 && clauses.length == 0)) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses(0)
                  val length = clause.effectiveParameters.length
                  val parameters: Seq[ScParameter] = if (i != -1) clause.effectiveParameters else clause.effectiveParameters.take(length - 1)
                  applyToParameters(parameters.map(param =>
                    (new Parameter(param), paramText(param, subst))), subst, canBeNaming = true, isImplicit = clause.isImplicit)
                }
              case method: FakePsiMethod =>
                if (method.params.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  buffer.append(method.params.
                          map((param: Parameter) => {
                    val buffer: StringBuilder = new StringBuilder("")
                    val paramType = param.paramType
                    val name = param.name
                    if (name != "") {
                      buffer.append(name)
                      buffer.append(": ")
                    }
                    buffer.append(ScType.presentableText(paramType))
                    if (param.isRepeated) buffer.append("*")

                    if (param.isDefault) buffer.append(" = _")

                    val isBold = if (method.params.indexOf(param) == index || (param.isRepeated && method.params.indexOf(param) <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                    val paramText = buffer.toString()
                    if (isBold) "<b>" + paramText + "</b>" else paramText
                  }).mkString(", "))
                }
              case method: PsiMethod =>
                val p = method.getParameterList
                if (p.getParameters.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  buffer.append(p.getParameters.
                          map((param: PsiParameter) => {
                    val buffer: StringBuilder = new StringBuilder("")
                    val list = param.getModifierList
                    if (list == null) return
                    val lastSize = buffer.length
                    for (a <- list.getAnnotations) {
                      if (lastSize != buffer.length) buffer.append(" ")
                      val element = a.getNameReferenceElement
                      if (element != null) buffer.append("@").append(element.getText)
                    }
                    if (lastSize != buffer.length) buffer.append(" ")

                    val name = param.name
                    if (name != null) {
                      buffer.append(name)
                    }
                    buffer.append(": ")
                    buffer.append(ScType.presentableText(subst.subst(param.exactParamType())))
                    if (param.isVarArgs) buffer.append("*")

                    val isBold = if (p.getParameters.indexOf(param) == index || (param.isVarArgs && p.getParameters.indexOf(param) <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                    val paramText = buffer.toString()
                    if (isBold) "<b>" + paramText + "</b>" else paramText
                  }).mkString(", "))
                }
            }
          case (constructor: ScPrimaryConstructor, subst: ScSubstitutor, i: Int) if constructor.isValid =>
            val clauses = constructor.effectiveParameterClauses
            if (clauses.length <= i) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val clause: ScParameterClause = clauses(i)
              applyToParameters(clause.effectiveParameters.map(param =>
                (new Parameter(param), paramText(param, subst))), subst, canBeNaming = true, isImplicit = clause.isImplicit)
            }
          case _ =>
        }
        val startOffset = buffer.indexOf("<b>")
        if (startOffset != -1) buffer.replace(startOffset, startOffset + 3, "")

        val endOffset = buffer.indexOf("</b>")
        if (endOffset != -1) buffer.replace(endOffset, endOffset + 4, "")

        if (buffer.toString != "")
          context.setupUIComponentPresentation(buffer.toString(), startOffset, endOffset, isGrey, false, false, color)
        else
          context.setUIComponentEnabled(false)
      case _ =>
    }
  }

  def tracksParameterIndex: Boolean = true

  trait Invocation {
    def element: PsiElement
    def parent: PsiElement = element.getParent
    def invocationCount: Int
    def callGeneric: Option[ScGenericCall] = None
    def callReference: Option[ScReferenceExpression]
  }

  object Invocation {
    private class CallInvocation(args: ScArgumentExprList) extends Invocation {
      override def element: PsiElement = args

      override def callGeneric: Option[ScGenericCall] = args.callGeneric

      override def invocationCount: Int = args.invocationCount

      override def callReference: Option[ScReferenceExpression] = args.callReference
    }
    private trait InfixInvocation extends Invocation {
      override def invocationCount: Int = 1

      override def callReference: Option[ScReferenceExpression] = {
        element.getParent match {
          case i: ScInfixExpr => Some(i.operation) 
        }
      }
    }
    private class InfixExpressionInvocation(expr: ScExpression) extends InfixInvocation {
      override def element: PsiElement = expr
    }
    private class InfixTupleInvocation(tuple: ScTuple) extends InfixInvocation {
      override def element: PsiElement = tuple
    }
    private class InfixUnitInvocation(u: ScUnitExpr) extends InfixInvocation {
      override def element: PsiElement = u
    }

    def getInvocation(elem: PsiElement): Option[Invocation] = {
      def create[T <: PsiElement](elem: T)(f: T => Invocation): Option[Invocation] = {
        elem.getParent match {
          case i: ScInfixExpr if i.getArgExpr == elem => Some(f(elem))
          case _ => None 
        }
      }
      
      elem match {
        case args: ScArgumentExprList => Some(new CallInvocation(args))
        case t: ScTuple => create(t)(new InfixTupleInvocation(_))
        case u: ScUnitExpr => create(u)(new InfixUnitInvocation(_))
        case e: ScExpression => create(e)(new InfixExpressionInvocation(_))
        case _ => None
      }
    }
  }


  /**
   * Returns context's argument psi and fill context items
   * by appropriate PsiElements (in which we can resolve)
   * @param context current context
   * @return context's argument expression
   */
  private def findCall(context: ParameterInfoContext): PsiElement = {
    val file = context.getFile
    val offset = context.getEditor.getCaretModel.getOffset
    val element = file.findElementAt(offset)
    if (element.isInstanceOf[PsiWhiteSpace])
    if (element == null) return null
    @tailrec
    def findArgs(elem: PsiElement): Option[Invocation] = {
      if (elem == null) return None
      val res = Invocation.getInvocation(elem)
      if (res.isDefined) return res
      findArgs(elem.getParent)
    }
    val argsOption: Option[Invocation] = findArgs(element)
    if (argsOption.isEmpty) return null
    val args = argsOption.get
    context match {
      case context: CreateParameterInfoContext =>
        args.parent match {
          case call: MethodInvocation =>
            val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
            def collectResult() {
              val canBeUpdate = call.getParent match {
                case assignStmt: ScAssignStmt if call == assignStmt.getLExpression => true
                case notExpr if !notExpr.isInstanceOf[ScExpression] || notExpr.isInstanceOf[ScBlockExpr] => true
                case _ => false
              }
              val count = args.invocationCount
              val gen = args.callGeneric.getOrElse(null: ScGenericCall)
              def collectSubstitutor(element: PsiElement): ScSubstitutor = {
                if (gen == null) return ScSubstitutor.empty
                val tp: Array[(String, String)] = element match {
                  case tpo: ScTypeParametersOwner => tpo.typeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p))).toArray
                  case ptpo: PsiTypeParameterListOwner => ptpo.getTypeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p)))
                  case _ => return ScSubstitutor.empty
                }
                val typeArgs: Seq[ScTypeElement] = gen.arguments
                val map = new collection.mutable.HashMap[(String, String), ScType]
                for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                  map += ((tp(i), typeArgs(i).calcType))
                }
                new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
              }
              def collectForType(typez: ScType) {
                ScType.extractClassType(typez, Some(file.getProject)) match {
                  case Some((clazz: PsiClass, subst: ScSubstitutor)) =>
                    for{
                      sign <- ScalaPsiUtil.getApplyMethods(clazz)
                      if ResolveUtils.isAccessible(sign.method, args.element)
                    } {
                      val subst1 = {
                        val signSubst = sign.substitutor
                        val collectSubst = collectSubstitutor(sign.method)
                        signSubst.followed(subst).followed(collectSubst)
                      }
                      res += ((new PhysicalSignature(sign.method, subst1), 0))
                    }
                    if (canBeUpdate) {
                      for{
                        sign <- ScalaPsiUtil.getUpdateMethods(clazz)
                        if ResolveUtils.isAccessible(sign.method, args.element)
                      } {
                        res += ((new PhysicalSignature(sign.method, subst.followed(sign.
                                substitutor).followed(collectSubstitutor(sign.method))), -1))
                      }
                    }
                  case _ =>
                }
              }
              def addImplicitConversionsForMagnets(obj: Object): Unit = {
                obj match {
                  case (sign: PhysicalSignature, i: Int) =>
                    sign.method match {
                      case function: ScFunction =>
                        val subst = sign.substitutor
                        val clauses = function.effectiveParameterClauses
                        if (clauses.length > i && i != -1) {
                          val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses(0)
                          val parameters: Seq[ScParameter] = clause.effectiveParameters
                          if (parameters.length == 1) { //do not consider more than one parameter
                            val paramType = parameters(0).getType(TypingContext.empty).getOrAny
                            ScType.extractClassType(paramType, Some(function.getProject)) match {
                              case Some((clazz: ScTypeDefinition, _)) =>
                                ScalaPsiUtil.getCompanionModule(clazz) match {
                                  case Some(obj: ScObject) if obj.isStatic =>
                                    val convertible = new ScImplicitlyConvertible(element, _ => None)
                                    val processor = new convertible.CollectImplicitsProcessor(true)
                                    processor.processType(obj.getType(TypingContext.empty).getOrAny, element)
                                    val candidates = processor.candidatesS
                                    for (candidate <- candidates) {
                                      candidate match {
                                        //todo: support other kind of implicits + functions with function return type
                                        case ScalaResolveResult(f: ScFunction, resolveSubst)
                                          if f.paramClauses.clauses.length > 0 &&
                                            !f.paramClauses.clauses(0).isImplicit &&
                                            f.paramClauses.clauses(0).parameters.length == 1 =>
                                          val newSubst = ScalaPsiUtil.inferMethodTypesArgs(f, resolveSubst)
                                          val returnType = newSubst.subst(f.returnType.getOrAny)
                                          if (returnType.conforms(paramType)) {
                                            val undefSubst = Conformance.undefinedSubst(paramType, returnType)
                                            undefSubst.getSubstitutor match {
                                              case Some(undefine) =>
                                                f.paramClauses.clauses(0).parameters(0).getType(TypingContext.empty) match {
                                                  case Success(implicitParamType, _) =>
                                                    val goodType = newSubst.followed(undefine).subst(implicitParamType).removeUndefines()
                                                    res += ((sign, i, goodType))
                                                  case _ =>
                                                }
                                              case _ =>
                                            }
                                          }
                                        case _ =>
                                      }
                                    }
                                  case _ => //todo: support for path dependent objects
                                }
                              case _ =>
                            }
                          }
                        }
                      case _ => //no support for Java
                    }
                  case _ =>
                }
              }
              args.callReference match {
                case Some(ref: ScReferenceExpression) =>
                  if (count > 1) {
                    //todo: missed case with last implicit call
                    ref.bind() match {
                      case Some(ScalaResolveResult(function: ScFunction, subst: ScSubstitutor)) if function.
                              effectiveParameterClauses.length >= count =>
                        val obj = (new PhysicalSignature(function, subst.followed(collectSubstitutor(function))), count - 1)
                        res += obj
                        addImplicitConversionsForMagnets(obj)
                      case _ =>
                        for (typez <- call.getEffectiveInvokedExpr.getType(TypingContext.empty)) //todo: implicit conversions
                        {collectForType(typez)}
                    }
                  } else {
                    val variants: Array[ResolveResult] = ref.getSameNameVariants
                    for {
                      variant <- variants
                      if !variant.getElement.isInstanceOf[PsiMember] ||
                          ResolveUtils.isAccessible(variant.getElement.asInstanceOf[PsiMember], ref)
                    } {
                      variant match {
                        //todo: Synthetic function
                        case ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor) =>
                          val obj = (new PhysicalSignature(method, subst.followed(collectSubstitutor(method))), 0)
                          res += obj
                          addImplicitConversionsForMagnets(obj)
                        case ScalaResolveResult(typed: ScTypedDefinition, subst: ScSubstitutor) =>
                          val typez = subst.subst(typed.getType(TypingContext.empty).getOrNothing) //todo: implicit conversions
                          collectForType(typez)
                        case _ =>
                      }
                    }
                  }
                case None =>
                  call match {
                    case call: ScMethodCall =>
                      for (typez <- call.getEffectiveInvokedExpr.getType(TypingContext.empty)) { //todo: implicit conversions
                        collectForType(typez)
                      }
                  }
              }
            }
            collectResult()
            context.setItemsToShow(res.toArray)
          case constr: ScConstructor =>
            val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
            val typeElement = constr.typeElement
            val i = constr.arguments.indexOf(args.element)
            ScType.extractClassType(typeElement.calcType, Some(file.getProject)) match {
              case Some((clazz: PsiClass, subst: ScSubstitutor)) =>
                clazz match {
                  case clazz: ScClass =>
                    clazz.constructor match {
                      case Some(constr: ScPrimaryConstructor) if i < constr.effectiveParameterClauses.length =>
                        typeElement match {
                          case gen: ScParameterizedTypeElement =>
                            val tp = clazz.typeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p)))
                            val typeArgs: Seq[ScTypeElement] = gen.typeArgList.typeArgs
                            val map = new collection.mutable.HashMap[(String, String), ScType]
                            for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                              map += ((tp(i), typeArgs(i).calcType))
                            }
                            val substitutor = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
                            res += ((constr, substitutor.followed(subst), i))
                          case _ => res += ((constr, subst, i))
                        }
                      case Some(_) if i == 0 => res += ""
                      case None => res += ""
                      case _ =>
                    }
                    for (constr <- clazz.functions if !constr.isInstanceOf[ScPrimaryConstructor] &&
                            constr.isConstructor && ((constr.clauses match {
                        case Some(x) => x.clauses.length
                        case None => 1
                      }) > i))
                      res += ((new PhysicalSignature(constr, subst), i))
                  case clazz: PsiClass if clazz.isAnnotationType =>
                    val resulting: (AnnotationParameters, Int) =
                      (AnnotationParameters(clazz.getMethods.toSeq.filter(_.isInstanceOf[PsiAnnotationMethod]).map(meth => (meth.name,
                        ScType.create(meth.getReturnType, meth.getProject, meth.getResolveScope),
                        meth.asInstanceOf[PsiAnnotationMethod].getDefaultValue))), i)
                    res += resulting
                  case clazz: PsiClass if !clazz.isInstanceOf[ScTypeDefinition] =>
                    for (constructor <- clazz.getConstructors) {
                      typeElement match {
                        case gen: ScParameterizedTypeElement =>
                          val tp = clazz.getTypeParameters.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p)))
                          val typeArgs: Seq[ScTypeElement] = gen.typeArgList.typeArgs
                          val map = new collection.mutable.HashMap[(String, String), ScType]
                          for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                            map += ((tp(i), typeArgs(i).calcType))
                          }
                          val substitutor = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, None)
                          res += ((new PhysicalSignature(constructor, substitutor.followed(subst)), i))
                        case _ => res += ((new PhysicalSignature(constructor, subst), i))
                      }
                    }
                  case _ =>
                }
              case _ =>
            }
            context.setItemsToShow(res.toArray)
          case self: ScSelfInvocation =>
            val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
            val i = self.arguments.indexOf(args.element)
            val clazz = PsiTreeUtil.getParentOfType(self, classOf[ScClass], true)
            clazz match {
              case clazz: ScClass =>
                clazz.constructor match {
                  case Some(constr: ScPrimaryConstructor) if i < constr.effectiveParameterClauses.length =>
                    res += ((constr, ScSubstitutor.empty, i))
                  case Some(constr) if i == 0 => res += ""
                  case None => res += ""
                  case _ =>
                }
                for {
                  constr <- clazz.functions
                  if !constr.isInstanceOf[ScPrimaryConstructor] &&
                    constr.isConstructor &&
                    constr.clauses.map(_.clauses.length).getOrElse(1) > i
                } {
                  if (!PsiTreeUtil.isAncestor(constr, self, true) &&
                    constr.getTextRange.getStartOffset < self.getTextRange.getStartOffset) {
                    res += ((new PhysicalSignature(constr, ScSubstitutor.empty), i))
                  }
                }
              case _ =>
            }
            context.setItemsToShow(res.toArray)
        }
      case context: UpdateParameterInfoContext =>
        var el = element
        while (el.getParent != args.element) el = el.getParent
        var index = 1
        for (expr <- getActualParameters(args.element) if expr != el) index += 1
        context.setCurrentParameter(index)
        context.setHighlightedParameter(el)
      case _ =>
    }
    args.element
  }
}

object ScalaFunctionParameterInfoHandler {
  case class AnnotationParameters(seq: Seq[(String, ScType, PsiAnnotationMemberValue)])
}

object ParameterInfoUtil {
  /**
   * Light green colour. Used for current resolve context showing.
   */
  val highlightedColor = new Color(231, 254, 234)
}
