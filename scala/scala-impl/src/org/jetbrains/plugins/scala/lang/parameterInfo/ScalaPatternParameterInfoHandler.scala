package org.jetbrains.plugins.scala
package lang
package parameterInfo

import java.awt.Color

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaPatternParameterInfoHandler extends ScalaParameterInfoHandler[ScPatternArgumentList, Any, ScPattern] {
  def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod]) //todo: ?
  }

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

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = null

  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: ScPatternArgumentList =>
        implicit val ctx: ProjectContext = args

        val color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        p match {
          //todo: join this match statement with same in FunctionParameterHandler to fix code duplicate.
          case (sign: PhysicalMethodSignature, _: Int) =>
            //i  can be -1 (it's update method)
            val method = sign.method
            val methodName = method.name

            val subst = sign.substitutor
            val returnType = method match {
              case function: ScFunction => subst(function.returnType.getOrAny)
              case method: PsiMethod => subst(method.getReturnType.toScType())
            }

            val isUnapplySeq = methodName == CommonNames.UnapplySeq
            val params = ScPattern.unapplySubpatternTypes(returnType, args, method.asInstanceOf[ScFunction]).zipWithIndex

            if (params.isEmpty) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              buffer.append(params.map {
                case (param, i) =>
                  val sb = StringBuilder.newBuilder
                  sb.append(param.presentableText(method))

                  val isSeq = isUnapplySeq && i == args.getArgsCount - 1
                  if (isSeq) sb.append("*")

                  val isBold =
                    if (i == index || (isSeq && i <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                  val paramTypeText = sb.toString()
                  val paramText = paramTextFor(sign, i, paramTypeText)

                  if (isBold) "<b>" + paramText + "</b>" else paramText
              }.mkString(", "))
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
          context.setupUIComponentPresentation(buffer.toString(), startOffset, endOffset, false, false, false, color)
        else
          context.setUIComponentEnabled(false)
      case _ =>
    }
  }

  /**
   * @return 'paramName: ParamType' if `sign` is a synthetic unapply method; otherwise 'ParamType'
   */
  private def paramTextFor(sign: PhysicalMethodSignature, o: Int, paramTypeText: String): String = {
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
            case Seq(sig) => sig.method.parameters.lift(o)
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



  override protected def findCall(context: ParameterInfoContext): ScPatternArgumentList = {
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null

    implicit val project: ProjectContext = file.projectContext
    val args: ScPatternArgumentList = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext =>
          args.getParent match {
            case constr: ScConstructorPattern =>
              val ref: ScStableCodeReference = constr.ref
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              if (ref != null) {
                val variants = ref.multiResolveScala(false)
                for (r <- variants) {
                  r.element match {
                    case fun: ScFunction if fun.parameters.nonEmpty =>
                      val substitutor = r.substitutor
                      val typeParameters = fun.typeParameters
                      val subst = if (typeParameters.isEmpty) substitutor
                      else {
                        val undefSubst = ScSubstitutor.bind(typeParameters)(UndefinedType(_))

                        val maybeSubstitutor = for {
                          Typeable(parameterType) <- fun.parameters.headOption
                          substituted = undefSubst(parameterType)
                          expectedType <- constr.expectedType

                          substitutor <- substituted.conformanceSubstitutor(expectedType)
                        } yield substitutor

                        maybeSubstitutor.fold(substitutor) {
                          _.followed(substitutor)
                        }
                      }
                      res += ((new PhysicalMethodSignature(fun, subst), 0))
                    case _ =>
                  }
                }
              }
              context.setItemsToShow(res.toArray)
            case _ =>
          }
        case context: UpdateParameterInfoContext =>
          var el = element
          while (el.getParent != args) el = el.getParent
          var index = 1
          for (pattern <- args.patterns if pattern != el) index += 1
          context.setCurrentParameter(index)
          context.setHighlightedParameter(el)
        case _ =>
      }
    }
    args
  }
}
