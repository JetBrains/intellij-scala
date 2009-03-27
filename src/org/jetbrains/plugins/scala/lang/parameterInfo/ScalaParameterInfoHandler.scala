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
import psi.api.toplevel.ScTyped
import psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
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
        def getRef(call: PsiElement): ScReferenceExpression = call match {
          case call: ScMethodCall => {
            call.getInvokedExpr match {
              case ref: ScReferenceExpression => ref
              case gen: ScGenericCall => gen.referencedExpr match {
                case ref: ScReferenceExpression => ref
                case _ => null
              }
              case call: ScMethodCall => getRef(call)
              case _ => null
            }
          }
          case _ => null
        }
        val ref = getRef(args.getParent)

        var color: Color = try {
          if (ref != null && ref.resolve == p) ParameterInfoUtil.highlightedColor
          else context.getDefaultParameterColor
        }
        catch {
          case e: PsiInvalidElementAccessException => context.getDefaultParameterColor
        }
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
                  val parameters = if (i != -1) clause.parameters else clause.parameters.subseq(0, length - 1)
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
    case class Ints(var i: Int)
    def getRef(call: ScMethodCall)(implicit deep: Boolean, calc: Ints): ScReferenceExpression = {
      call.getInvokedExpr match {
        case ref: ScReferenceExpression => ref
        case gen: ScGenericCall => gen.referencedExpr match {
          case ref: ScReferenceExpression => ref
          case _ => null
        }
        case call: ScMethodCall if deep => {
          calc.i += 1
          getRef(call)(true, calc)
        }
        case _ => null
      }
    }
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args: ScArgumentExprList = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext => {
          args.getParent match {
            case call: ScMethodCall => {
              val parent = call.getParent
              val canBeUpdate = parent match {
                case ass: ScAssignStmt if call == ass.getLExpression => true
                case notExpr if !notExpr.isInstanceOf[ScExpression] || notExpr.isInstanceOf[ScBlockExpr] => true
                case _ => false
              }
              implicit val bool = false
              implicit val calc: Ints = Ints(0)
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              val ref = getRef(call)
              if (ref != null) {
                val name = ref.refName
                val variants: Array[Object] = ref.getSameNameVariants
                for (variant <- variants if !variant.isInstanceOf[PsiMember] ||
                        ResolveUtils.isAccessible(variant.asInstanceOf[PsiMember], ref)) {
                  variant match {
                    //todo: Synnthetic Function
                    case method: PsiMethod => {
                      val getSign: PhysicalSignature = {
                        ref.qualifier match {
                          case Some(x: ScExpression) => new PhysicalSignature(method, ScType.extractClassType(x.getType) match {
                            case Some((_, subst)) => subst
                            case _ => ScSubstitutor.empty
                          })
                          case None => new PhysicalSignature(method, ScSubstitutor.empty)
                        }
                      }
                      ref.getParent match {
                        case gen: ScGenericCall => {
                          var substitutor = ScSubstitutor.empty
                          val tp = method match {
                            case fun: ScFunction => fun.typeParameters.map(_.name)
                            case _ => method.getTypeParameters.map(_.getName)
                          }
                          val typeArgs: Seq[ScTypeElement] = gen.typeArgs.typeArgs
                          val map = new collection.mutable.HashMap[String, ScType]
                          for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                            map += Tuple(tp(i), typeArgs(i).calcType)
                          }
                          substitutor = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
                          res += (new PhysicalSignature(method, getSign.substitutor.followed(substitutor)), 0)
                        }
                        case _ => res += (getSign, 0)
                      }
                    }
                    case obj: ScObject => {
                      //apply method
                      for (n <- ScalaPsiUtil.getApplyMethods(obj)) {
                        val meth: ScFunction = n.method.asInstanceOf[ScFunction]
                        ref.getParent match {
                          case gen: ScGenericCall => {
                            val tp = meth.typeParameters.map(_.name)
                            val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, gen)
                            res += (new PhysicalSignature(meth, n.substitutor.followed(substitutor)), 0)
                          }
                          case _ => res += (n, 0)
                        }
                      }
                      //update method
                      if (canBeUpdate) {
                        for (n <- ScalaPsiUtil.getUpdateMethods(obj)) {
                          val meth: ScFunction = n.method.asInstanceOf[ScFunction]
                          ref.getParent match {
                            case gen: ScGenericCall => {
                              //todo: can be generic
                            }
                            case _ => res += (n, -1)
                          }
                        }
                      }
                    }
                    case cl: ScClass if cl.hasModifierProperty("case") => {
                      cl.constructor match {
                        case Some(constr: ScPrimaryConstructor) => {
                          ref.getParent match {
                            case gen: ScGenericCall => {
                              val tp = cl.typeParameters.map(_.name)
                              val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, gen)
                              res += (constr, substitutor, 0)
                            }
                            case _ => res += (constr, ScSubstitutor.empty, 0)
                          }
                        }
                        case None => res += ""
                      }
                    }
                    case v: ScTyped => v.calcType match {
                      case fun: ScFunctionType => res += fun
                      case typez => ScType.extractClassType(typez) match {
                        case Some((clazz: PsiClass, subst)) => {
                          //apply mehtod
                          for (n <- ScalaPsiUtil.getApplyMethods(clazz)) {
                            val expr = call.getInvokedExpr
                            n.method match {
                              case meth: ScFunction => expr match {
                                case gen: ScGenericCall => {
                                  val tp = meth.typeParameters.map(_.name)
                                  val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, gen)
                                  res += (new PhysicalSignature(meth, n.substitutor.followed(substitutor).followed(subst)), 0)
                                }
                                case _ => res += (new PhysicalSignature(meth, n.substitutor.followed(subst)), 0)
                              }
                              case _ => { //todo: java case

                              }
                            }
                          }
                          //update method
                          if (canBeUpdate) {
                            for (n <- ScalaPsiUtil.getUpdateMethods(clazz)) {
                            val expr = call.getInvokedExpr
                            n.method match {
                              case meth: ScFunction => expr match {
                                case gen: ScGenericCall => {
                                  //todo: can be generic
                                }
                                case _ => res += (new PhysicalSignature(meth, n.substitutor.followed(subst)), -1)
                              }
                              case _ => { //todo: java case

                              }
                            }
                          }
                          }
                        }
                        case None =>
                      }
                    }
                    case _ =>
                  }
                }
              } else {
                var expr: ScExpression = call.getInvokedExpr
                val typez = expr match {
                  case gen: ScGenericCall => gen.referencedExpr.getType
                  case _ => expr.getType
                }
                typez match {
                  case fun: ScFunctionType => res += fun
                  case _ => ScType.extractClassType(typez) match {
                    case Some((clazz: PsiClass, subst)) => {
                      def end = {
                        //apply method
                        for (n <- ScalaPsiUtil.getApplyMethods(clazz)) {
                          n.method match {
                            case meth: ScFunction => expr match {
                              case gen: ScGenericCall => {
                                val tp = meth.typeParameters.map(_.name)
                                val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, gen)
                                res += (new PhysicalSignature(meth, n.substitutor.followed(substitutor).followed(subst)), 0)
                              }
                              case _ => res += (new PhysicalSignature(meth, n.substitutor.followed(subst)), 0)
                            }
                            case _ => {//todo: java case

                            }
                          }
                        }
                        //update method
                        if (canBeUpdate) {
                          for (n <- ScalaPsiUtil.getUpdateMethods(clazz)) {
                            n.method match {
                              case meth: ScFunction => expr match {
                                case gen: ScGenericCall => {
                                  //todo: can be generic (just method)
                                }
                                case _ => res += (new PhysicalSignature(meth, n.substitutor.followed(subst)), -1)
                              }
                              case _ => { //todo: java case

                              }
                            }
                          }
                        }
                      }
                      val ints = Ints(0)
                      val ref = getRef(call)(true, ints)
                      if (ref != null) {
                        ref.resolve match {
                          case cl: ScClass if cl.hasModifierProperty("case") => {
                            cl.constructor match {
                              case Some(constr: ScPrimaryConstructor) if ints.i < constr.parameterList.clauses.length => {
                                ref.getParent match {
                                  case gen: ScGenericCall => {
                                    val tp = cl.typeParameters.map(_.name)
                                    val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, gen)
                                    res += (constr, substitutor, ints.i)
                                  }
                                  case _ => res += (constr, ScSubstitutor.empty, ints.i)
                                }
                              }
                              case None => res += ""
                              case _ => end
                            }
                          }
                          case _ => end
                        }
                      } else end

                    }
                    case None =>
                  }
                }
              }
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
                              res += (constr, substitutor.followed(subst), i)
                            }
                            case _ => res += (constr, subst, i)
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
                        res += (new PhysicalSignature(n.method, subst), i)
                    }
                    case clazz: PsiClass if !clazz.isInstanceOf[ScTypeDefinition] => {
                      for (constr <- clazz.getConstructors) {
                        typeElement match {
                          case gen: ScParameterizedTypeElement => {
                            val tp = clazz.getTypeParameters.map(_.getName)
                            val typeArgs: Seq[ScTypeElement] = gen.typeArgList.typeArgs
                            val map = new collection.mutable.HashMap[String, ScType]
                            for (i <- 0 to Math.min(tp.length, typeArgs.length) - 1) {
                              map += Tuple(tp(i), typeArgs(i).calcType)
                            }
                            val substitutor = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
                            res += (new PhysicalSignature(constr, substitutor.followed(subst)), i)
                          }
                          case _ => res += (new PhysicalSignature(constr, subst), i)
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