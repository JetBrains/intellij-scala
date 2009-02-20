package org.jetbrains.plugins.scala.lang.parameterInfo

import _root_.org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import _root_.scala.collection.mutable.ArrayBuffer
import annotations.Nullable
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
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
import psi.api.base.ScConstructor
import psi.api.expr._
import psi.api.statements.params.{ScParameter, ScParameters, ScParameterClause}
import psi.api.statements.{ScFunction, ScValue, ScVariable}
import psi.api.toplevel.ScTyped
import psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import psi.ScalaPsiElement
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
    context.getParameterOwner match {
      case args: ScArgumentExprList => {
        args.getParent match {
          case call: ScMethodCall => {
            def getRef(call: ScMethodCall): ScReferenceExpression = {
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
            val ref = getRef(call)

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
              case sign: PhysicalSignature => {
                val subst = sign.substitutor
                sign.method match {
                  case method: ScFunction => {
                    val clauses = method.paramClauses.clauses
                    if (clauses.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                    else {
                      val clause: ScParameterClause = clauses(0)
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
                        buffer.append(paramType.getPresentableText)

                        val isBold = if (p.getParameters.indexOf(param) == index) true
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
          case _ => //todo: constructors and patterns
        }
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
              def getRef(call: ScMethodCall): ScReferenceExpression = {
                call.getInvokedExpr match {
                  case ref: ScReferenceExpression => ref
                  case gen: ScGenericCall => gen.referencedExpr match {
                    case ref: ScReferenceExpression => ref
                    case _ => null
                  }
                  case _ => null
                }
              }
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              val ref = getRef(call)
              if (ref != null) {
                val name = ref.refName
                val variants: Array[Object] = ref.getSameNameVariants
                for (variant <- variants) {
                  variant match {
                    case method: PsiMethod => {
                      val getSign: PhysicalSignature = {
                        ref.qualifier match {
                          case Some(x: ScExpression) => new PhysicalSignature(method, ScType.extractDesignated(x.getType) match {
                            case Some((_, subst)) => subst
                            case _ => null
                          })
                          case None => new PhysicalSignature(method, ScSubstitutor.empty)
                        }
                      }
                      ref.getParent match {
                        case gen: ScGenericCall => res += Tuple(getSign, gen.typeArgs)
                        case _ => res += getSign
                      }
                    }
                    case v: ScTyped => v.calcType match {
                      case fun: ScFunctionType => res += fun
                      case typez => ScType.extractClassType(typez) match {
                        case Some((clazz: PsiClass, subst)) => {
                          for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
                          if n.method.getName == "apply"  &&
                                  (clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static"))) res += n
                        }
                        case None =>
                      }
                    }
                    case cl: PsiClass => //todo: Objects apply, case classses
                  }
                }
              } else {
                val expr: ScExpression = call.getInvokedExpr
                val typez = expr.getType
                typez match {
                  case fun: ScFunctionType => res += fun
                  case _ => ScType.extractClassType(typez) match {
                    case Some((clazz: PsiClass, subst)) => {
                      for ((n: PhysicalSignature, _) <- TypeDefinitionMembers.getMethods(clazz)
                        if n.method.getName == "apply" && (clazz.isInstanceOf[ScObject] || !n.method.hasModifierProperty("static"))) res += n
                    }
                    case None =>
                  }
                }
              }
              context.setItemsToShow(res.toArray)
            }
            case _ => //todo: constructors and patterns
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