package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.util

import com.incors.plaf.alloy.{r, bp}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.{OverridingMethodsSearch, ReferencesSearch}
import com.intellij.refactoring.changeSignature._
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.rename.ResolveSnapshotProvider.ResolveSnapshot
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiTypedDefinitionWrapper, ScFunctionWrapper, StaticPsiMethodWrapper, StaticPsiTypedDefinitionWrapper}

import scala.collection.mutable.ArrayBuffer

/**
* Nikolay.Tropin
* 2014-08-10
*/
class ScalaChangeSignatureUsageProcessor extends ChangeSignatureUsageProcessor with ScalaChangeSignatureUsageHandler {

  override def findUsages(info: ChangeInfo): Array[UsageInfo] = {
    val results = ArrayBuffer[UsageInfo]()
    info match {
      case jInfo: JavaChangeInfo =>
        val method = jInfo.getMethod
        val methods = ArrayBuffer(method)
        val functions = ArrayBuffer[ScFunction]()
        OverridingMethodsSearch.search(method).forEach { m: PsiMethod =>
          methods += m
          m match {
            case f: ScFunction => functions += f
            case fw: ScFunctionWrapper => functions += fw.function
            case _ =>
          }
          true
        }
        methods.foreach(findMethodRefUsages(_, results))
        findParameterUsages(jInfo, functions, results)
      case _ =>
    }
    results.toArray
  }

  override def shouldPreviewUsages(changeInfo: ChangeInfo, usages: Array[UsageInfo]): Boolean = false

  override def processPrimaryMethod(changeInfo: ChangeInfo): Boolean = false

  override def processUsage(changeInfo: ChangeInfo,
                            usageInfo: UsageInfo,
                            beforeMethodChange: Boolean,
                            usages: Array[UsageInfo]): Boolean = {
    if (!UsageUtil.scalaUsage(usageInfo)) return false

    if (beforeMethodChange) {
      processOverriderUsages(changeInfo, usageInfo, beforeMethodChange)
    } else {
      processSimpleUsages(changeInfo, usageInfo, beforeMethodChange)
    }

    true
  }

  override def findConflicts(info: ChangeInfo,
                             refUsages: Ref[Array[UsageInfo]]): MultiMap[PsiElement, String] = {
    val usages = refUsages.get()
    val result = new MultiMap[PsiElement, String]()

    usages.foreach {
      case ScalaOverriderUsageInfo(u: OverriderClassParamUsageInfo) => ConflictsUtil.addClassParameterConflicts(u.overrider, info, result)
      case ScalaOverriderUsageInfo(u: OverriderValUsageInfo) => ConflictsUtil.addBindingPatternConflicts(u.overrider, info, result)
      case _ =>
    }
    result
  }

  override def registerConflictResolvers(snapshots: util.List[ResolveSnapshot],
                                         resolveSnapshotProvider: ResolveSnapshotProvider,
                                         usages: Array[UsageInfo],
                                         changeInfo: ChangeInfo): Unit = {}

  //in this method one can ask or fill undefined default values
  override def setupDefaultValues(changeInfo: ChangeInfo,
                                  refUsages: Ref[Array[UsageInfo]],
                                  project: Project): Boolean = true


  private def processOverriderUsages(change: ChangeInfo, usage: UsageInfo, beforeMethodChange: Boolean): Unit = {
    usage match {
      case ScalaOverriderUsageInfo(scUsage) =>
        handleChangedName(change, usage)
        handleReturnTypeChange(change, scUsage)
        handleChangedParameters(change, scUsage)
      case _ =>
    }
  }

  private def processSimpleUsages(change: ChangeInfo, usage: UsageInfo, beforeMethodChange: Boolean): Unit = {
    usage match {
      case ScalaOverriderUsageInfo(_) =>
      case _ if !change.isGenerateDelegate =>
        handleChangedName(change, usage)
        handleParametersUsages(change, usage)
        handleUsageArguments(change, usage)
      case _ =>
    }
  }

  private def findMethodRefUsages(method: PsiMethod, results: ArrayBuffer[UsageInfo]): Unit = {
    val unwrapped = method match {
      case fw: ScFunctionWrapper => fw.function
      case tdw: PsiTypedDefinitionWrapper => tdw.typedDefinition
      case st: StaticPsiTypedDefinitionWrapper => st.typedDefinition
      case st: StaticPsiMethodWrapper => st.method
      case m => m
    }
    ReferencesSearch.search(unwrapped).forEach { ref: PsiReference =>
      val refElem = ref.getElement
      refElem match {
        case (refExpr: ScReferenceExpression) childOf (mc: ScMethodCall) => results += MethodCallUsageInfo(refExpr, mc)
        case ChildOf(infix @ ScInfixExpr(_, `refElem`, _)) => results += InfixExprUsageInfo(infix)
        case ChildOf(postfix @ ScPostfixExpr(_, `refElem`)) => results += PostfixExprUsageInfo(postfix)
        case (refExpr: ScReferenceExpression) childOf (und: ScUnderscoreSection) => results += MethodValueUsageInfo(und)
        case refExpr: ScReferenceExpression => results += RefExpressionUsage(refExpr)
        case _ =>
      }
      true
    }
  }

  private def findParameterUsages(changeInfo: ChangeInfo,
                                  functions: Seq[ScFunction],
                                  results: ArrayBuffer[UsageInfo]): Unit = {
    for {
      paramInfo <- changeInfo.getNewParameters
      oldIdx = paramInfo.getOldIndex
      if oldIdx >= 0
      fun <- functions
      if fun.parameters.size > oldIdx
      param = fun.parameters(oldIdx)
      if param.name != paramInfo.getName
    } {
      addParameterUsages(param, results)
    }
  }

  private def addParameterUsages(scParam: ScParameter, results: ArrayBuffer[UsageInfo]) {
    val scope: SearchScope = scParam.owner.getUseScope
    ReferencesSearch.search(scParam, scope, false).forEach { ref: PsiReference =>
      val element = ref.getElement match {
        case refElem: ScReferenceElement => results += new ParameterUsageInfo(scParam, refElem)
        case _ =>
      }
      true
    }
  }
}
