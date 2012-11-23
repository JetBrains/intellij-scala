package org.jetbrains.plugins.scala
package lang
package parameterInfo

import _root_.java.lang.{Class, String}
import collection.mutable.ArrayBuffer
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.tree.IElementType
import _root_.java.util.Set
import com.intellij.lang.parameterInfo._
import com.intellij.psi.util.PsiTreeUtil
import psi.api.base.ScStableCodeReferenceElement
import psi.api.statements.ScFunction
import psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import psi.ScalaPsiUtil
import psi.types._
import com.intellij.util.ArrayUtil
import java.awt.Color
import lexer.ScalaTokenTypes
import psi.api.base.patterns.{ScPattern, ScConstructorPattern, ScPatternArgumentList}
import lang.resolve.ScalaResolveResult
import result.TypingContext
import extensions.{toPsiNamedElementExt, toPsiClassExt}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaPatternParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScPatternArgumentList, Any, ScPattern] {
  def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod]) //todo: ?
  }

  def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(patternArgumentList: ScPatternArgumentList): Array[ScPattern] = patternArgumentList.patterns.toArray

  def getArgumentListClass: Class[ScPatternArgumentList] = classOf[ScPatternArgumentList]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: java.util.Set[Class[_]] = {
    val set = new java.util.HashSet[Class[_]]()
    set.add(classOf[ScConstructorPattern])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): ScPatternArgumentList = {
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScPatternArgumentList = {
    findCall(context)
  }

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = ArrayUtil.EMPTY_OBJECT_ARRAY

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = null

  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: ScPatternArgumentList => {
        var color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        p match {
          //todo: join this match statement with same in FunctionParameterHandler to fix code duplicate.
          case (sign: PhysicalSignature, i: Int) => {
            //i  can be -1 (it's update method)
            val methodName = sign.method.name

            val subst = sign.substitutor
            val p = sign.method match {
              case method: ScFunction => {
                subst.subst(method.returnType.getOrAny)
              }
              case method: PsiMethod => {
                subst.subst(ScType.create(method.getReturnType, method.getProject))
              }
            }
            val qual = ScType.extractClass(p) match {
              case Some(clazz) => clazz.qualifiedName
              case _ => ""
            }
            val generics: Seq[ScType] = p match {
              case pt: ScParameterizedType => pt.typeArgs
              case JavaArrayType(arg) => Seq(arg)
              case _ => Seq.empty
            }
            if (qual != "scala.Option" || generics.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              var o = -1 //index for right search bold parameter
              val params = for (t <- (generics(0) match {
                case tuple: ScTupleType => tuple.components
                case tp => ScType.extractClassType(tp, Some(context.getParameterOwner.getProject)) match {
                  case Some((clazz, _)) if clazz != null && clazz.qualifiedName != null &&
                          clazz.qualifiedName.startsWith("scala.Tuple") => {
                    tp match {
                      case pt: ScParameterizedType => pt.typeArgs.toSeq
                      case JavaArrayType(arg) => Seq(arg)
                      case _ => Seq[ScType](tp)
                    }
                  }
                  case _ => Seq[ScType](tp)
                }
              })) yield {
                o += 1
                (t, o)
              }
              buffer.append(params.map {
                case (param, o) =>
                  val buffer: StringBuilder = new StringBuilder("")
                  buffer.append(ScType.presentableText(param))
                  val isSeq = methodName == "unapplySeq" && (ScType.extractClass(param) match {
                    case Some(clazz) => clazz.qualifiedName == "scala.Seq"
                    case _ => false
                  })
                  if (isSeq) {
                    buffer.delete(0, buffer.indexOf("[") + 1)
                    buffer.deleteCharAt(buffer.length - 1)
                    buffer.append("*")
                  }
                  val isBold = if (o == index || (isSeq && o <= index)) true
                  else {
                    //todo: check type
                    false
                  }
                  val paramTypeText = buffer.toString
                  val paramText = paramTextFor(sign, o, paramTypeText)

                  if (isBold) "<b>" + paramText + "</b>" else paramText
              }.mkString(", "))
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

  /**
   * @return 'paramName: ParamType' if `sign` is a synthetic unapply method; otherwise 'ParamType'
   */
  private def paramTextFor(sign: PhysicalSignature, o: Int, paramTypeText: String): String = {
    if (sign.method.name == "unapply") {
      sign.method match {
        case fun: ScFunction if fun.parameters.headOption.exists(_.name == "x$0") =>
          val companionClass: Option[ScClass] = Option(fun.containingClass) match {
            case Some(x: ScObject) => ScalaPsiUtil.getCompanionModule(x) match {
              case Some(x: ScClass) => Some(x)
              case _ => None
            }
            case _ => None
          }

          companionClass match {
            case Some(cls) => ScalaPsiUtil.nthConstructorParam(cls, o) match {
              case Some(param) =>
                if (param.isRepeatedParameter) {
                  paramTypeText // Not handled yet.
                } else {
                  param.name + ": " + paramTypeText // SCL-3006
                }
              case None => paramTypeText
            }
            case None => paramTypeText
          }
        case fun: ScFunction =>
          // Look for a corresponding apply method beside the unapply method.
          // TODO also check types correspond, allowing for overloading
          val applyParam: Option[PsiParameter] = ScalaPsiUtil.getApplyMethods(fun.containingClass) match {
            case Seq(sig) => sig.method.getParameterList.getParameters.lift(o)
            case _ => None
          }
          applyParam match {
            case Some(param) => param.getName + ": " + paramTypeText
            case None => paramTypeText
          }
        case _ =>
          paramTypeText
      }
    } else paramTypeText
  }

  def showParameterInfo(element: ScPatternArgumentList, context: CreateParameterInfoContext): Unit = {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def updateParameterInfo(o: ScPatternArgumentList, context: UpdateParameterInfoContext): Unit = {
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

  def tracksParameterIndex: Boolean = true

  private def findCall(context: ParameterInfoContext): ScPatternArgumentList = {
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args: ScPatternArgumentList = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext => {
          args.getParent match {
            case constr: ScConstructorPattern => {
              val ref: ScStableCodeReferenceElement = constr.ref
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              if (ref != null) {
                val name = ref.refName
                val variants: Array[ResolveResult] = ref.multiResolve(false)
                for (variant <- variants if variant.isInstanceOf[ScalaResolveResult]) {
                  val r = variant.asInstanceOf[ScalaResolveResult]
                  r.element match {
                    case fun: ScFunction if fun.parameters.nonEmpty =>
                      val substitutor = r.substitutor
                      val subst = if (fun.typeParameters.length == 0) substitutor
                      else {
                        val undefSubst = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
                          s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(new ScTypeParameterType(p,
                            substitutor))))
                        val emptySubst: ScSubstitutor = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
                          s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), p.upperBound.getOrAny))
                        val result = fun.parameters(0).getType(TypingContext.empty)
                        if (result.isEmpty) substitutor
                        else {
                          val funType = undefSubst.subst(result.get)
                          constr.expectedType match {
                            case Some(tp) =>
                              val t = Conformance.conforms(tp, funType)
                              if (t) {
                                val undefSubst = Conformance.undefinedSubst(tp, funType)
                                undefSubst.getSubstitutor match {
                                  case Some(newSubst) => newSubst.followed(substitutor)
                                  case _ => substitutor
                                }
                              } else substitutor
                            case _ => substitutor
                          }
                        }
                      }
                      res += ((new PhysicalSignature(fun, subst), 0))
                    case _ =>
                  }
                }
              }
              context.setItemsToShow(res.toArray)
            }
            case _ =>
          }
        }
        case context: UpdateParameterInfoContext => {
          var el = element
          while (el.getParent != args) el = el.getParent
          var index = 1
          for (pattern <- args.patterns if pattern != el) index += 1
          context.setCurrentParameter(index)
          context.setHighlightedParameter(el)
        }
        case _ =>
      }

    }
    return args
  }
}