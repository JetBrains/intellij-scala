package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter


import java.util.Collections

import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.refactoring.introduceParameter.{IntroduceParameterData, IntroduceParameterMethodUsagesProcessor}
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.conversion.JavaToScala
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.06.2009
 */

class ScalaIntroduceParameterMethodUsagesProcessor extends IntroduceParameterMethodUsagesProcessor {
  def processAddDefaultConstructor(data: IntroduceParameterData, usage: UsageInfo,
                                   usages: Array[UsageInfo]): Boolean = {
    if (usage.getElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return true
    false
  }

  def processAddSuperCall(data: IntroduceParameterData, usage: UsageInfo, usages: Array[UsageInfo]): Boolean = {
    if (usage.getElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return true
    false
  }

  def findConflicts(introduceParameterData: IntroduceParameterData, usageInfos: Array[UsageInfo],
                    psiElementStringMultiMap: MultiMap[PsiElement, String]): Unit = {
    
  }

  def processChangeMethodSignature(data: IntroduceParameterData, usage: UsageInfo,
                                   usages: Array[UsageInfo]): Boolean = {
    val psiManager = PsiManager.getInstance(data.getProject)
    val methodLike = usage.getElement match {
      case ml: ScMethodLike => ml
      case _ => return true
    }
    val paramName = data.getParameterName
    val paramType: ScType = data match {
      case proc: ScalaIntroduceParameterProcessor => proc.getScalaForcedType
      case _ => ScType.create(data.getForcedType, data.getProject, data.getMethodToReplaceIn.getResolveScope)
    }
    val defaultTail: String = data match {
      case proc: ScalaIntroduceParameterProcessor if proc.isDeclareDefault &&
        methodLike == data.getMethodToSearchFor =>
        " = " + proc.argText
      case _ => ""
    }

    //todo: resolve conflicts with fields

    //remove parameters
    val paramsToRemove = data.getParametersToRemove
    val params = methodLike.parameterList.params
    for (i <- paramsToRemove.toNativeArray.reverseIterator) {
      params(i).remove()
    }

    //add parameter
    val needSpace = ScalaNamesUtil.isIdentifier(paramName + ":")
    val paramText = paramName + (if (needSpace) " : " else ": ") + ScType.canonicalText(paramType) + defaultTail
    val param = methodLike match {
      case f: ScFunction => createParameterFromText(paramText, psiManager)
      case pc: ScPrimaryConstructor => createClassParameterFromText(paramText, psiManager)
    }
    ScalaPsiUtil.adjustTypes(param)
    methodLike.addParameter(param)

    false
  }

  private def isElementInUsages(data: IntroduceParameterData, element: PsiElement, usages: Array[UsageInfo]): Boolean = {
    for (usage <- usages) {
      usage.getElement match {
        case fun: ScFunction =>
          if (PsiTreeUtil.isAncestor(fun, element, false)) return true
        case _ =>
      }
    }
    false
  }

  def processChangeMethodUsage(data: IntroduceParameterData, usage: UsageInfo, usages: Array[UsageInfo]): Boolean = {
    val element = usage.getElement
    if (element.getLanguage != ScalaFileType.SCALA_LANGUAGE) return true
    data match {
      case data: ScalaIntroduceParameterProcessor if data.isDeclareDefault => return true //shouldn't do anything
      case _ =>
    }
    val psiManager: PsiManager = PsiManager.getInstance(data.getProject)

    element match {
      case ref: ScReferenceExpression =>
        val (callCandidate: ScExpression, needToReplace: Option[ScExpression]) = ref.getParent match {
          case u: ScUnderscoreSection => return false
          case call: ScMethodCall =>
            if (call.args.isBraceArgs) {
              val text: String = s"${ref.getText}(${call.args.getText})"
              (createExpressionFromText(text, psiManager), Some(call))
            }
            else (call, None)
          case postf: ScPostfixExpr =>
            val text: String = s"${postf.operand.getText}.${postf.operation.getText}()"
            (createExpressionFromText(text, psiManager), Some(postf))
          case pref: ScPrefixExpr =>
            val text: String = s"${pref.operand.getText}.unary_${pref.operand.getText}()"
            (createExpressionFromText(text, psiManager), Some(pref))
          case inf: ScInfixExpr =>
            (createEquivMethodCall(inf), Some(inf))
          case _ =>
            (createExpressionFromText(s"${ref.getText}()", psiManager), Some(ref))
        }
        val call = needToReplace.map(_.replaceExpression(callCandidate, removeParenthesis = true))
                .getOrElse(callCandidate).asInstanceOf[ScMethodCall]
        val isInUsages = isElementInUsages(data, element.getParent, usages)
        val expression: ScExpression =
          if (isInUsages) createExpressionFromText(data.getParameterName, psiManager)
          else {
            data match {
              case proc: ScalaIntroduceParameterProcessor =>
                if (!proc.hasDefaults) {
                  val text = proc.argText
                  createExpressionFromText(text, psiManager)
                }
                else {
                  val text = proc.getParameterName + " = " + proc.argText
                  createExpressionFromText(text, psiManager)
                }
              case _ =>
                val text = JavaToScala.convertPsiToText(data.getParameterInitializer.getExpression)
                createExpressionFromText(text, psiManager)
            }
          }
        CodeEditUtil.setNodeGenerated(expression.getNode, true)
        val args = call.args
        val exprs = args.exprs
        if (exprs.length == 0) {
          args.addExpr(expression)
        } else {
          data match {
            case data: ScalaIntroduceParameterProcessor =>
              if (data.posNumber == 0) args.addExpr(expression)
              else {
                val anchor =
                  if (data.posNumber >= exprs.length)
                    exprs(exprs.length - 1)
                  else
                    exprs(data.posNumber - 1)
                anchor match {
                  case ass: ScAssignStmt =>
                    expression match {
                      case _: ScAssignStmt => args.addExprAfter(expression, anchor)
                      case _ =>
                        val text = data.getParameterName + " = " + expression.getText
                        val newExpr = createExpressionFromText(text, psiManager)
                        CodeEditUtil.setNodeGenerated(newExpr.getNode, true)
                        args.addExprAfter(newExpr, anchor)
                    }
                  case _ => args.addExprAfter(expression, anchor)
                }
              }
            case _ =>
              if (exprs.length == 0) args.addExpr(expression)
              else if (!data.getMethodToSearchFor.isVarArgs) {
                args.addExprAfter(expression, exprs(exprs.length - 1))
              } else if (data.getMethodToSearchFor.getParameterList.getParametersCount > exprs.length) {
                args.addExprAfter(expression, exprs(exprs.length - 1))
              } else if (data.getMethodToSearchFor.getParameterList.getParametersCount == 1) {
                args.addExpr(expression)
              } else {
                val anchor = exprs(data.getMethodToSearchFor.getParameterList.getParametersCount - 2)
                args.addExprAfter(expression, anchor)
              }
          }
        }

      case _ => //todo: patterns, this, constructors, new usages
    }

    false
  }

  def findConflicts(data: IntroduceParameterData, usages: Array[UsageInfo]): java.util.Map[PsiElement, String] = 
    Collections.emptyMap[PsiElement, String]

  def isMethodUsage(usage: UsageInfo): Boolean = {
    val elem = usage.getElement
    elem match {
      case ref: ScReferenceExpression =>
        ref.getParent match {
          case _: ScMethodCall => true
          case _ => ref.bind() match {
            case Some(ScalaResolveResult(_: ScFunction, _)) => true
            case _ => false
          }
        }
      case _ => false
    }
  }
}