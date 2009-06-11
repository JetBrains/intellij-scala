package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter


import com.intellij.refactoring.introduceParameter.{IntroduceParameterData, IntroduceParameterMethodUsagesProcessor}
import java.lang.String
import com.intellij.usageView.UsageInfo
import java.util.{Collections, List}
import psi.api.expr.{ScMethodCall, ScReferenceExpression}
import psi.api.statements.ScFunction
import resolve.ScalaResolveResult
/**
 * User: Alexander Podkhalyuzin
 * Date: 11.06.2009
 */

class ScalaIntroduceParameterMethodUsagesProcessor extends IntroduceParameterMethodUsagesProcessor{
  def processAddDefaultConstructor(data: IntroduceParameterData, usage: UsageInfo, usages: Array[UsageInfo]): Boolean = false

  def processAddSuperCall(data: IntroduceParameterData, usage: UsageInfo, usages: Array[UsageInfo]): Boolean = false

  def processChangeMethodSignature(data: IntroduceParameterData, usage: UsageInfo, usages: Array[UsageInfo]): Boolean = {
    if (!usage.getElement.isInstanceOf[ScFunction]) return true
    false
  }

  def processChangeMethodUsage(data: IntroduceParameterData, usage: UsageInfo, usages: Array[UsageInfo]): Boolean = {
    if (!isMethodUsage(usage)) return true
    false
  }

  def findConflicts(data: IntroduceParameterData, usages: Array[UsageInfo]): List[String] = Collections.emptyList[String]

  def isMethodUsage(usage: UsageInfo): Boolean = {
    val elem = usage.getElement
    elem match {
      case ref: ScReferenceExpression => {
        ref.getParent match {
          case _: ScMethodCall => return true
          case _ => ref.bind match {
            case Some(ScalaResolveResult(_: ScFunction, _)) => return true
            case _ => return false
          }
        }
      }
      case _ => return false
    }
  }
}