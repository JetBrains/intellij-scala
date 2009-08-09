package org.jetbrains.plugins.scala
package lang
package parameterInfo

import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import _root_.java.lang.{Class, String}
import collection.mutable.{ArrayBuffer, HashSet}
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi._
import psi.api.statements.params.{ScParameterClause, ScParameter}
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.psi.tree.IElementType
import _root_.java.util.Set
import com.intellij.lang.parameterInfo._



import com.intellij.psi.util.PsiTreeUtil
import psi.api.base.{ScPrimaryConstructor, ScStableCodeReferenceElement, ScConstructor}
import psi.api.expr.{ScGenericCall, ScArgumentExprList, ScMethodCall, ScExpression}

import psi.api.statements.ScFunction
import psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import psi.ScalaPsiUtil
import psi.types._
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import com.intellij.util.ArrayUtil
import java.awt.Color
import lexer.ScalaTokenTypes
import psi.api.base.patterns.{ScPattern, ScConstructorPattern, ScPatternArgumentList}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaPatternParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScPatternArgumentList, Any, ScPattern] {
  def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(patternArgumentList: ScPatternArgumentList): Array[ScPattern] = patternArgumentList.patterns.toArray

  def getArgumentListClass: Class[ScPatternArgumentList] = classOf[ScPatternArgumentList]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: Set[Class[_]] = {
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
        p match { //todo: join this match statement with same in FunctionParameterHandler to fix code duplicate.
          case x: String if x == "" => {
            buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
          }
          case (sign: PhysicalSignature, i: Int) => { //i  can be -1 (it's update method)
            val subst = sign.substitutor
            val p = sign.method match {
              case method: ScFunction => {
                subst.subst(method.returnType)
              }
              case method: PsiMethod => {
                subst.subst(ScType.create(method.getReturnType, method.getProject))
              }
            }
            val qual = ScType.extractClassType(p) match {
              case Some((clazz, substitutor)) => clazz.getQualifiedName
              case _ => ""
            }
            val generics: Seq[ScType] = p match {
              case pt: ScParameterizedType => pt.typeArgs
              case _ => Seq.empty
            }
            if (qual != "scala.Option" || generics.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              var o = -1 //index for right search bold parameter
              val params = for (t <-(generics(0) match {
                case tuple: ScTupleType => tuple.components
                case tp => ScType.extractClassType(tp) match {
                  case Some((clazz, _)) if clazz != null && clazz.getQualifiedName != null &&
                          clazz.getQualifiedName.startsWith("scala.Tuple") => {
                    tp match {
                      case pt: ScParameterizedType => pt.typeArgs.toSeq
                      case _ => Seq[ScType](tp)
                    }
                  }
                  case _ => Seq[ScType](tp)
                }
              })) yield {
                o += 1
                (t, o)
              }
              buffer.append(params.
                      map((paramX: (ScType,Int)) => {
                val param = paramX._1
                val o = paramX._2
                val buffer: StringBuilder = new StringBuilder("")
                buffer.append(ScType.presentableText(param))
                val isSeq = sign.method.getName == "unapplySeq" && (ScType.extractClassType(param) match {
                  case Some((clazz, substitutor)) => clazz.getQualifiedName == "scala.Seq"
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
                val paramText = buffer.toString
                if (isBold) "<b>" + paramText + "</b>" else paramText
              }).mkString(", "))
            }
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

  private def findCall(context: ParameterInfoContext): ScPatternArgumentList = { //todo: Expected type
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
                val variants: Array[PsiElement] = ref.getSameNameVariants.map(r => r.getElement)
                for (variant <- variants if !variant.isInstanceOf[PsiMember] ||
                        ResolveUtils.isAccessible(variant.asInstanceOf[PsiMember], ref)) {
                  variant match {
                    case obj: ScObject => {
                      //unapply method
                      for (n <- ScalaPsiUtil.getUnapplyMethods(obj)) {
                        res += ((n, 0))
                      }
                    }
                    case clazz: ScClass if clazz.isCase => {
                      clazz.constructor match {
                        case Some(constr: ScPrimaryConstructor) => {
                          res += ((constr, ScSubstitutor.empty, 0))
                        }
                        case None => res += ""
                      }
                    }
                    case clazz: PsiClass if !clazz.isInstanceOf[ScTypeDefinition] => {
                      //unapply method
                      for (n <- ScalaPsiUtil.getUnapplyMethods(clazz)) {
                        res += ((n, 0))
                      }
                    }
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