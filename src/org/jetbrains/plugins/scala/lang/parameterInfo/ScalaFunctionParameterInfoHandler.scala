package org.jetbrains.plugins.scala
package lang
package parameterInfo

import _root_.org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.lang.ASTNode
import com.intellij.lang.parameterInfo._

import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.hash.HashSet
import java.awt.Color
import java.lang.{Class, String}
import java.util.Set
import lexer.ScalaTokenTypes
import psi.api.base.types.{ScParameterizedTypeElement, ScTypeElement}
import psi.api.base.{ScConstructor, ScPrimaryConstructor}
import psi.api.expr._
import psi.api.statements.params.{ScParameter, ScParameterClause}
import psi.api.statements.{ScFunction}
import psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import psi.impl.statements.params.ScParameterImpl
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import psi.{ScalaPsiUtil}
import result.{Success, TypeResult, TypingContext}

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.01.2009
 */

class ScalaFunctionParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScArgumentExprList, Any, ScExpression] {
  def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(argExprList: ScArgumentExprList): Array[ScExpression] = argExprList.exprs.toArray

  def getArgumentListClass: Class[ScArgumentExprList] = classOf[ScArgumentExprList]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: Set[Class[_]] = {
    val set = new HashSet[Class[_]]()
    set.add(classOf[ScMethodCall])
    set.add(classOf[ScConstructor])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): ScArgumentExprList = {
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScArgumentExprList = {
    findCall(context)
  }

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = {
    p match {
      case x: ScFunction => {
        x.parameters.toArray
      }
      case _ => ArrayUtil.EMPTY_OBJECT_ARRAY
    }
  }

  def showParameterInfo(element: ScArgumentExprList, context: CreateParameterInfoContext): Unit = {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = {
    val allElements = JavaCompletionUtil.getAllPsiElements(item.asInstanceOf[LookupItem[_]])

    if (allElements != null &&
        allElements.size > 0 &&
        allElements.get(0).isInstanceOf[PsiMethod]) {
      return allElements.toArray(new Array[Object](allElements.size));
    }
    return null
  }

  def updateParameterInfo(o: ScArgumentExprList, context: UpdateParameterInfoContext): Unit = {
    if (context.getParameterOwner != o) context.removeHint
    val offset = context.getOffset
    var child = o.getNode.getFirstChildNode
    var i = 0
    while (child != null && child.getStartOffset < offset) {
      if (child.getElementType == ScalaTokenTypes.tCOMMA) i = i + 1
      child = child.getTreeNext
    }
    context.setCurrentParameter(i)
  }


  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: ScArgumentExprList => {
        var color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        var isGrey = false
        //todo: var isGreen = true
        var namedMode = false
        def applyToParameters(parameters: Seq[ScParameter], subst: ScSubstitutor, canBeNaming: Boolean) {
          if (parameters.length > 0) {
            def paramText(param: ScParameter) = {
              ScalaDocumentationProvider.parseParameter(param, (t: ScType) => ScType.presentableText(subst.subst(t)))
            }
            var k = 0
            val exprs: Seq[ScExpression] = args.exprs
            val used = new Array[Boolean](parameters.length)
            while (k < parameters.length) {
              val namedPrefix = "["
              val namedPostfix = "]"
              def appendFirst(useGrey: Boolean = false) {
                val getIt = used.indexOf(false)
                used(getIt) = true
                if (namedMode) buffer.append(namedPrefix)
                val param: ScParameter = parameters(getIt)
                buffer.append(paramText(param))
                if (namedMode) buffer.append(namedPostfix)
              }
              def doNoNamed(expr: ScExpression) {
                if (namedMode) {
                  isGrey = true
                  appendFirst()
                } else {
                  for (exprType <- expr.getType(TypingContext.empty)) {
                    val getIt = used.indexOf(false)
                    used(getIt) = true
                    val param: ScParameter = parameters(getIt)
                    val paramType = param.getType(TypingContext.empty) getOrElse Nothing
                    if (!exprType.conforms(paramType)) isGrey = true
                    buffer.append(paramText(param))
                  }
                }
              }
              if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
                      parameters(parameters.length - 1).isRepeatedParameter)) {
                buffer.append("<b>")
              }
              if (k < index && !isGrey) {
                //slow checking
                if (k >= exprs.length) { //shouldn't be
                  appendFirst(true)
                  isGrey = true
                } else {
                  exprs(k) match {
                    case assign@NamedAssignStmt(name) => {
                      val ind = parameters.findIndexOf(_.name == name)
                      if (ind == -1 || used(ind) == true) {
                        doNoNamed(assign)
                      } else {
                        namedMode = true
                        used(ind) = true
                        val param: ScParameter = parameters(ind)
                        buffer.append(namedPrefix).append(paramText(param)).append(namedPostfix)
                        assign.getRExpression match {
                          case Some(expr: ScExpression) => {
                            for (exprType <- expr.getType(TypingContext.empty)) {
                              val paramType = param.getType(TypingContext.empty).getOrElse(Nothing)
                              if (!exprType.conforms(paramType)) isGrey = true
                            }
                          }
                          case _ => isGrey = true
                        }
                      }
                    }
                    case expr: ScExpression => {
                      doNoNamed(expr)
                    }
                  }
                }
              } else {
                //fast checking
                if (k >= exprs.length) {
                  appendFirst()
                } else {
                  exprs(k) match {
                    case NamedAssignStmt(name) => {
                      val ind = parameters.findIndexOf(_.name == name)
                      if (ind == -1 || used(ind) == true) {
                        appendFirst()
                      } else {
                        namedMode = true
                        used(ind) = true
                        buffer.append(namedPrefix).append(paramText(parameters(ind))).append(namedPostfix)
                      }
                    }
                    case _ => appendFirst()
                  }
                }
              }
              if (k == index || (k == parameters.length - 1 && index >= parameters.length &&
                      parameters(parameters.length - 1).isRepeatedParameter)) {
                buffer.append("</b>")
              }
              k = k + 1
              if (k != parameters.length) buffer.append(", ")
            }
            if (!isGrey && exprs.length > parameters.length && index >= parameters.length) {
              if (!namedMode && parameters(parameters.length - 1).isRepeatedParameter) {
                val paramType = parameters(parameters.length - 1).getType(TypingContext.empty).getOrElse(Nothing)
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
          case x: String if x == "" => {
            buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
          }
          case (sign: PhysicalSignature, i: Int) => { //i  can be -1 (it's update method)
            val subst = sign.substitutor
            sign.method match {
              case method: ScFunction => {
                val clauses = method.paramClauses.clauses
                if (clauses.length <= i || (i == -1 && clauses.length == 0)) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses(0)
                  val length = clause.parameters.length
                  val parameters: Seq[ScParameter] = if (i != -1) clause.parameters else clause.parameters.take(length - 1)
                  applyToParameters(parameters, subst, true)
                }
              }
              case method: PsiMethod => {
                val p = method.getParameterList
                if (p.getParameters.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  buffer.append(p.getParameters.
                          map((param: PsiParameter) => {
                    val buffer: StringBuilder = new StringBuilder("")
                    val list = param.getModifierList
                    if (list == null) return;
                    val lastSize = buffer.length
                    for (a <- list.getAnnotations) {
                      if (lastSize != buffer.length) buffer.append(" ")
                      val element = a.getNameReferenceElement();
                      if (element != null) buffer.append("@").append(element.getText)
                    }
                    if (lastSize != buffer.length) buffer.append(" ")
                    val paramType = param.getType

                    val name = param.getName
                    if (name != null) {
                      buffer.append(name)
                    }
                    buffer.append(": ")
                    buffer.append(ScType.presentableText(subst.subst(ScType.create(paramType, method.getProject))))

                    val isBold = if (p.getParameters.indexOf(param) == index || (param.isVarArgs && p.getParameters.indexOf(param) <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                    val paramText = buffer.toString
                    if (isBold) "<b>" + paramText + "</b>" else paramText
                  }).mkString(", "))
                }
              }
            }
          }
          case ScFunctionType(_, params: Seq[ScType]) => {
            if (params.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            val parameters: Array[ScParameter] = new Array[ScParameter](params.length)
            for (i <- 0 until params.length) {
              parameters(i) = new ScParameterImpl(null: ASTNode) { //todo: replace this buggy ideology
                override def getName = "v" + (i + 1)

                override def name(): String = "v" + (i + 1)

                override def getType(ctx: TypingContext): TypeResult[ScType] = {
                  return Success(params(i), None)
                }

                override def annotations: Seq[ScAnnotation] = Seq.empty

                override def isRepeatedParameter: Boolean = false

                override def isDefaultParam: Boolean = false
              }
            }
            applyToParameters(collection.immutable.Seq(parameters.toSeq: _*), ScSubstitutor.empty, true)
          }
          case (constructor: ScPrimaryConstructor, subst: ScSubstitutor, i: Int) => {
            val clauses = constructor.parameterList.clauses
            if (clauses.length <= i) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val clause: ScParameterClause = clauses(i)
              applyToParameters(clause.parameters, subst, true)
            }
          }
          case _ =>
        }
        val startOffset = buffer.indexOf("<b>")
        if (startOffset != -1) buffer.replace(startOffset, startOffset + 3, "")

        val endOffset = buffer.indexOf("</b>")
        if (endOffset != -1) buffer.replace(endOffset, endOffset + 4, "")

        if (buffer.toString != "")
          context.setupUIComponentPresentation(buffer.toString, startOffset, endOffset, isGrey, false, false, color)
        else
          context.setUIComponentEnabled(false)
      }
      case _ =>
    }
  }

  def tracksParameterIndex: Boolean = true

  /**
   * Returns context's ScArgumentExprList and fill context items
   * by appropriate PsiElements (in which we can resolve)
   * @param context current context
   * @return context's argument expression
   */
  private def findCall(context: ParameterInfoContext): ScArgumentExprList = {
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args: ScArgumentExprList = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext => {
          args.getParent match {
            case call: ScMethodCall => {
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              def collectResult {
                val canBeUpdate = call.getParent match {
                  case assignStmt: ScAssignStmt if call == assignStmt.getLExpression => true
                  case notExpr if !notExpr.isInstanceOf[ScExpression] || notExpr.isInstanceOf[ScBlockExpr] => true
                  case _ => false
                }
                val count = args.invocationCount
                val gen = args.callGeneric.getOrElse(null: ScGenericCall)
                def collectSubstitutor(element: PsiElement): ScSubstitutor = {
                  if (gen == null) return ScSubstitutor.empty
                  val tp: Array[String] = element match {
                    case tpo: ScTypeParametersOwner => tpo.typeParameters.map(_.name).toArray
                    case ptpo: PsiTypeParameterListOwner => ptpo.getTypeParameters.map(_.getName)
                    case _ => return ScSubstitutor.empty
                  }
                  val typeArgs: Seq[ScTypeElement] = gen.arguments
                  val map = new collection.mutable.HashMap[String, ScType]
                  for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                    map += Tuple(tp(i), typeArgs(i).calcType)
                  }
                  new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
                }
                def collectForType(typez: ScType) {
                  typez match {
                    case ft: ScFunctionType => res += ft
                    case _ =>
                  }
                  ScType.extractClassType(typez) match {
                    case Some((clazz: PsiClass, subst: ScSubstitutor)) => {
                      for{
                        sign <- ScalaPsiUtil.getApplyMethods(clazz)
                        if ResolveUtils.isAccessible(sign.method, args)
                      } {
                        res += ((new PhysicalSignature(sign.method, subst.followed(sign.
                                substitutor).followed(collectSubstitutor(sign.method))), 0))
                      }
                      if (canBeUpdate) {
                        for{
                          sign <- ScalaPsiUtil.getUpdateMethods(clazz)
                          if ResolveUtils.isAccessible(sign.method, args)
                        } {
                          res += ((new PhysicalSignature(sign.method, subst.followed(sign.
                                  substitutor).followed(collectSubstitutor(sign.method))), -1))
                        }
                      }
                    }
                    case _ =>
                  }
                }
                args.callReference match {
                  case Some(ref: ScReferenceExpression) => {
                    val name = ref.refName
                    if (count > 1) {
                      //todo: missed case with last implicit call
                      ref.bind match {
                        case Some(ScalaResolveResult(function: ScFunction, subst: ScSubstitutor)) if function.
                                paramClauses.clauses.length >= count => {
                          res += ((new PhysicalSignature(function, subst.followed(collectSubstitutor(function))), count - 1))
                          return
                        }
                        case Some(ScalaResolveResult(clazz: ScClass, subst: ScSubstitutor)) if clazz.isCase &&
                                (clazz.constructor match {
                                  case Some(constructor: ScPrimaryConstructor) if constructor.parameterList.clauses.
                                             length >= count => true
                                  case _ => false
                                })=> {
                          val constructor = clazz.constructor.get
                          res += ((constructor, subst.followed(collectSubstitutor(clazz)), count - 1))
                          return
                        }
                        case _ => {
                          for (typez <- call.getInvokedExpr.getType(TypingContext.empty)) //todo: implicit conversions
                          {collectForType(typez)}
                        }
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
                          case ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor) => {
                            res += ((new PhysicalSignature(method, subst.followed(collectSubstitutor(method))), 0))
                          }
                          case ScalaResolveResult(clazz: ScClass, subst: ScSubstitutor) if clazz.isCase => {
                            clazz.constructor match {
                              case Some(constructor) => res += ((constructor, subst.followed(collectSubstitutor(clazz)), 0))
                              case None => res += ""
                            }
                          }
                          case ScalaResolveResult(typed: ScTypedDefinition, subst: ScSubstitutor) => {
                            val typez = subst.subst(typed.getType(TypingContext.empty).getOrElse(Nothing)) //todo: implicit conversions
                              collectForType(typez)
                          }
                          case _ =>
                        }
                      }
                    }
                  }
                  case None => {
                    for (typez <- call.getInvokedExpr.getType(TypingContext.empty)) { //todo: implicit conversions
                      collectForType(typez)
                    }
                  }
                }
              }
              collectResult
              context.setItemsToShow(res.toArray)
            }
            case constr: ScConstructor => {
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              val typeElement = constr.typeElement
              val i = constr.arguments.indexOf(args)
              ScType.extractClassType(typeElement.calcType) match {
                case Some((clazz: PsiClass, subst: ScSubstitutor)) => {
                  clazz match {
                    case clazz: ScClass => {
                      clazz.constructor match {
                        case Some(constr: ScPrimaryConstructor) if i < constr.parameterList.clauses.length => {
                          typeElement match {
                            case gen: ScParameterizedTypeElement => {
                              val tp = clazz.typeParameters.map(_.name)
                              val typeArgs: Seq[ScTypeElement] = gen.typeArgList.typeArgs
                              val map = new collection.mutable.HashMap[String, ScType]
                              for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                                map += Tuple(tp(i), typeArgs(i).calcType)
                              }
                              val substitutor = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
                              res += ((constr, substitutor.followed(subst), i))
                            }
                            case _ => res += ((constr, subst, i))
                          }
                        }
                        case None => res += ""
                        case _ =>
                      }
                      for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
                        if !n.method.isInstanceOf[ScPrimaryConstructor] && n.method.getName == "this" &&
                                n.method.getContainingClass == clazz &&
                                (!n.method.isInstanceOf[ScFunction]
                                || (n.method.asInstanceOf[ScFunction].clauses match {
                          case Some(x) => x.clauses.length
                          case None => 1
                        }) > i))
                        res += ((new PhysicalSignature(n.method, subst), i))
                    }
                    case clazz: PsiClass if !clazz.isInstanceOf[ScTypeDefinition] => {
                      for (constructor <- clazz.getConstructors) {
                        typeElement match {
                          case gen: ScParameterizedTypeElement => {
                            val tp = clazz.getTypeParameters.map(_.getName)
                            val typeArgs: Seq[ScTypeElement] = gen.typeArgList.typeArgs
                            val map = new collection.mutable.HashMap[String, ScType]
                            for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                              map += Tuple(tp(i), typeArgs(i).calcType)
                            }
                            val substitutor = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
                            res += ((new PhysicalSignature(constructor, substitutor.followed(subst)), i))
                          }
                          case _ => res += ((new PhysicalSignature(constructor, subst), i))
                        }
                      }
                    }
                    case _ =>
                  }
                }
                case _ =>
              }
              context.setItemsToShow(res.toArray)
            }
            case _: ScSelfInvocation => // todo:
          }
        }
        case context: UpdateParameterInfoContext => {
          var el = element
          while (el.getParent != args) el = el.getParent
          var index = 1
          for (expr <- args.exprs if expr != el) index += 1
          context.setCurrentParameter(index)
          context.setHighlightedParameter(el)
        }
        case _ =>
      }
    }
    return args
  }
}

object ParameterInfoUtil {
  /**
   * Light green colour. Used for current resolve context showing.
   */
  val highlightedColor = new Color(231, 254, 234)
}
