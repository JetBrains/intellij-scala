package org.jetbrains.plugins.scala.lang.parameterInfo

import _root_.org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

import _root_.scala.collection.mutable.ArrayBuffer
import annotations.Nullable

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.lang.parameterInfo._

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.hash.HashSet
import java.awt.Color
import java.lang.{Class, String}
import java.util.Set
import lexer.ScalaTokenTypes
import psi.api.base.patterns.{ScConstructorPattern, ScPatternArgumentList}
import psi.api.base.types.{ScParameterizedTypeElement, ScTypeElement}
import psi.api.base.{ScConstructor, ScPrimaryConstructor}
import psi.api.expr._
import psi.api.statements.params.{ScParameter, ScArguments, ScParameters, ScParameterClause}
import psi.api.statements.{ScFunction, ScValue, ScVariable}
import psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import psi.api.toplevel.{ScTypeParametersOwner, ScTyped}
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import _root_.org.jetbrains.plugins.scala.util.ScalaUtils
import psi.{ScalaPsiUtil, ScalaPsiElement}
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
                  val parameters = if (i != -1) clause.parameters else clause.parameters.take(length - 1)
                  if (parameters.length > 0) {
                    if (clause.isImplicit) buffer.append("implicit ")
                    buffer.append(parameters.
                            map((param: ScParameter) => {
                      val isBold = if (parameters.indexOf(param) == index || (param.isRepeatedParameter && index >= parameters.indexOf(param))) true
                      else {
                        //todo: check type
                        false
                      }
                      val paramText = ScalaDocumentationProvider.parseParameter(param, (t: ScType) => ScType.presentableText(subst.subst(t)))
                      if (isBold) "<b>" + paramText + "</b>" else paramText
                    }).mkString(", "))
                  } else buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
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
          case ScFunctionType(_, params) => {
            if (params.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            buffer.append(params.map((param: ScType) => {
              val paramText = "p" + params.indexOf(param) + ": " + ScType.presentableText(param)
              val isBold = if (params.indexOf(param) == index) true
              else {
                //todo: check type
                false
              }
              if (isBold) "<b>" + paramText + "</b>" else paramText
            }).mkString(", "))
          }
          case (constr: ScPrimaryConstructor, subst: ScSubstitutor, i: Int) => {
            val clauses = constr.parameterList.clauses
            if (clauses.length <= i) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val clause: ScParameterClause = clauses(i)
              if (clause.parameters.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
              else {
                if (clause.isImplicit) buffer.append("implicit ")
                buffer.append(clause.parameters.
                        map((param: ScParameter) => {
                  val isBold = if (clause.parameters.indexOf(param) == index) true
                  else {
                    //todo: check type
                    false
                  }
                  val paramText = ScalaDocumentationProvider.parseParameter(param, (t: ScType) => ScType.presentableText(subst.subst(t)))
                  if (isBold) "<b>" + paramText + "</b>" else paramText
                }).mkString(", "))
              }
            }
          }
          case _ =>
        }
        val isGrey = buffer.indexOf("<g>")
        if (isGrey != -1) buffer.replace(isGrey, isGrey + 3, "")
        val startOffset = buffer.indexOf("<b>")
        if (startOffset != -1) buffer.replace(startOffset, startOffset + 3, "")

        val endOffset = buffer.indexOf("</b>")
        if (endOffset != -1) buffer.replace(endOffset, endOffset + 4, "")

        if (buffer.toString != "")
          context.setupUIComponentPresentation(buffer.toString, startOffset, endOffset, false, false, false, color)
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
                  val tp = element match {
                    case tpo: ScTypeParametersOwner => tpo.typeParameters.map(_.name)
                    case ptpo: PsiTypeParameterListOwner => ptpo.getTypeParameters.map(_.getName)
                    case _ => return ScSubstitutor.empty
                  }
                  val typeArgs: Seq[ScTypeElement] = gen.typeArgs.typeArgs
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
                          val constructor = clazz.constructor match {case Some(constructor) => constructor}
                          res += ((constructor, subst.followed(collectSubstitutor(clazz)), count - 1))
                          return
                        }
                        case _ => {
                          val typez = call.getInvokedExpr.cachedType //todo: implicit conversions
                          collectForType(typez)
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
                            res += ((clazz, subst.followed(collectSubstitutor(clazz))))
                          }
                          case ScalaResolveResult(typed: ScTyped, subst: ScSubstitutor) => {
                            val typez = subst.subst(typed.calcType) //todo: implicit conversions
                            collectForType(typez)
                          }
                          case _ =>
                        }
                      }
                    }
                  }
                  case None => {
                    val typez = call.getInvokedExpr.cachedType //todo: implicit conversions
                    collectForType(typez)
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
                        if n.method.getName == "this" && (!n.method.isInstanceOf[ScFunction]
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