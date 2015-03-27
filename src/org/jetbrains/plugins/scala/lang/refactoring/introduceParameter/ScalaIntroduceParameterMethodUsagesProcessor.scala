package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter


import java.util.Collections

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduceParameter.{IntroduceParameterData, IntroduceParameterMethodUsagesProcessor}
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.conversion.JavaToScala
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
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
    if (!usage.getElement.isInstanceOf[ScFunction]) return true
    val fun: ScFunction = usage.getElement.asInstanceOf[ScFunction]
    val paramName = data.getParameterName
    val paramType: ScType = data match {
      case proc: ScalaIntroduceParameterProcessor => proc.getScalaForcedType
      case _ => ScType.create(data.getForcedType, data.getProject, data.getMethodToReplaceIn.getResolveScope)
    }
    val defaultTail: String = data match {
      case proc: ScalaIntroduceParameterProcessor if proc.isDeclareDefault &&
        fun == data.getMethodToSearchFor =>
        " = " + proc.getScalaExpressionToSearch.getText
      case _ => ""
    }

    //todo: resolve conflicts with fields

    //remove parameters
    val paramsToRemove = data.getParametersToRemove
    val params = fun.parameters
    for (i <- paramsToRemove.toNativeArray.reverseIterator) {
      params(i).remove()
    }

    //add parameter
    val needSpace = ScalaNamesUtil.isIdentifier(paramName + ":")
    val paramText = paramName + (if (needSpace) " : " else ": ") + ScType.canonicalText(paramType) + defaultTail
    val param = ScalaPsiElementFactory.createParameterFromText(paramText, fun.getManager)
    ScalaPsiUtil.adjustTypes(param)
    fun.addParameter(param)

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


    element match {
      case ref: ScReferenceExpression =>
        val call: ScMethodCall = ref.getParent match {
          case u: ScUnderscoreSection => return false
          case call: ScMethodCall =>
            if (call.args.isBraceArgs) {
              val newCall = ScalaPsiElementFactory.createExpressionFromText(
                ref.getText + "(" + call.args.getText + ")", element.getManager
              )
              newCall match {
                case c: ScMethodCall =>
                  call.replaceExpression(c, removeParenthesis = true).asInstanceOf[ScMethodCall]
                case _ => return false
              }
            } else call
          case postf: ScPostfixExpr =>
            val newPostf = ScalaPsiElementFactory.createExpressionFromText(
              postf.operand.getText + "." + postf.operation.getText + "()", element.getManager
            )
            newPostf match {
              case call: ScMethodCall =>
                postf.replaceExpression(call, removeParenthesis = true).asInstanceOf[ScMethodCall]
              case _ => return false
            }
          case pref: ScPrefixExpr =>
            val newPref = ScalaPsiElementFactory.createExpressionFromText(
              pref.operand.getText + ".unary_" + pref.operand.getText + "()", element.getManager
            )
            newPref match {
              case call: ScMethodCall =>
                pref.replaceExpression(call, removeParenthesis = true).asInstanceOf[ScMethodCall]
              case _ => return false
            }
          case inf: ScInfixExpr =>
            val newInf = ScalaPsiElementFactory.createExpressionFromText(
              inf.getBaseExpr.getText + "." + inf.operation.getText + "(" + inf.getArgExpr.getText + ")", element.getManager
            )
            newInf match {
              case call: ScMethodCall =>
                inf.replaceExpression(call, removeParenthesis = true).asInstanceOf[ScMethodCall]
              case _ => return false
            }
          case _ =>
            val newCall = ScalaPsiElementFactory.createExpressionFromText(
              ref.getText + "()", element.getManager
            )
            newCall match {
              case call: ScMethodCall =>
                ref.replaceExpression(call, removeParenthesis = true).asInstanceOf[ScMethodCall]
              case _ => return false
            }
        }
        val isInUsages = isElementInUsages(data, element.getParent, usages)
        val expression: ScExpression =
          if (isInUsages) {
            ScalaPsiElementFactory.createExpressionFromText(data.getParameterName, element.getManager)
          }
          else {
            data match {
              case proc: ScalaIntroduceParameterProcessor =>
                if (!proc.hasDefaults) {
                  val text = proc.getScalaExpressionToSearch.getText
                  try {
                    ScalaPsiElementFactory.createExpressionFromText(text, element.getManager)
                  } catch {
                    case e: Exception =>
                      proc.getScalaExpressionToSearch
                  }
                }
                else {
                  val text = proc.getParameterName + " = " + proc.getScalaExpressionToSearch.getText
                  try {
                    ScalaPsiElementFactory.createExpressionFromText(text, element.getManager)
                  } catch {
                    case e: Exception =>
                      proc.getScalaExpressionToSearch
                  }
                }
              case _ =>
                val text = JavaToScala.convertPsiToText(data.getParameterInitializer.getExpression)
                try {
                  ScalaPsiElementFactory.createExpressionFromText(text, element.getManager)
                } catch {
                  case e: Exception =>
                    ScalaPsiElementFactory.createExpressionFromText(data.getParameterName, element.getManager)
                }
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
                        val newExpr = try {
                          ScalaPsiElementFactory.createExpressionFromText(text, element.getManager)
                        } catch {
                          case e: Exception =>
                            expression
                        }
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