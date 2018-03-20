package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import java.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.{OverridingMethodsSearch, ReferencesSearch}
import com.intellij.refactoring.changeSignature.{MethodCallUsageInfo => JavaCallUsageInfo, _}
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.rename.ResolveSnapshotProvider.ResolveSnapshot
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

import _root_.scala.annotation.tailrec
import _root_.scala.collection.JavaConverters._
import _root_.scala.collection.mutable.ArrayBuffer

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
        findMethodRefUsages(method, results, searchInJava = false)

        val synthetics = method match {
          case ScPrimaryConstructor.ofClass(clazz) if clazz.isCase =>
            val inExistingClasses = (Seq(clazz) ++ clazz.baseCompanionModule).flatMap {
              _.allSynthetics
            }
            val inFakeCompanion = clazz.fakeCompanionModule.toSeq.flatMap {
              _.members
            }
            inExistingClasses ++ inFakeCompanion
          case _ => Seq.empty
        }

        val overriders = OverridingMethodsSearch.search(method).findAll.asScala.toSeq ++
                ScalaOverridingMemberSearcher.search(method).toSeq
        val methods = (method +: overriders ++: synthetics).map {
          case isWrapper(m) => m
          case other => other
        }.distinct

        if (info.isParameterSetOrOrderChanged || info.isParameterNamesChanged) {
          methods.foreach {
            case m: PsiMethod =>
              findParameterUsages(jInfo, m, results)
            case _ =>
          }
        }

        (overriders ++ synthetics).foreach {
          case named: PsiNamedElement =>
            val usageInfo = ScalaNamedElementUsageInfo(named)
            if (usageInfo != null) results += usageInfo

            findMethodRefUsages(named, results, searchInJava = synthetics.contains(named))
          case _ =>
        }
      case _ =>
    }
    results.toArray
  }

  override def shouldPreviewUsages(changeInfo: ChangeInfo, usages: Array[UsageInfo]): Boolean = false

  override def processPrimaryMethod(changeInfo: ChangeInfo): Boolean = changeInfo match {
    case scalaChange: ScalaChangeInfo =>
      scalaChange.function match {
        case f: ScFunction => processNamedElementUsage(changeInfo, FunUsageInfo(f))
        case pc: ScPrimaryConstructor => processNamedElementUsage(changeInfo, PrimaryConstructorUsageInfo(pc))
        case _ =>
      }
      true
    case _ => false
  }

  override def processUsage(changeInfo: ChangeInfo,
                            usageInfo: UsageInfo,
                            beforeMethodChange: Boolean,
                            usages: Array[UsageInfo]): Boolean = {

    def isLastUsage = !beforeMethodChange && usageInfo == usages.last

    def updateNamedElements(): Unit = {
      usages.foreach {
        case namedInfo: ScalaNamedElementUsageInfo =>
          val element = ScalaPsiUtil.nameContext(namedInfo.namedElement)
          val text = element.getText
          element match {
            case _: ScVariableDefinition | _: ScPatternDefinition =>
              val newElement = ScalaPsiElementFactory.createDefinitionWithContext(text, element.getContext, element)
              element.getParent.addAfter(newElement, element)
              element.delete()
            case _: ScVariableDeclaration | _: ScValueDeclaration =>
              val newElement = ScalaPsiElementFactory.createDeclarationFromText(text, element.getContext, element)
              element.getParent.addAfter(newElement, element)
              element.delete()
            case _ =>
          }
        case _ =>
      }
    }

    //need to add arguments from previous clauses as parameters to default argument function
    def addArgumentsToDefaultParamInJava(): Unit = {
      val newParams = changeInfo match {
        case sc: ScalaChangeInfo => sc.newParams
        case _ => return
      }

      if (newParams.size <= 1) return

      val cumulSize = newParams.scanLeft(0)(_ + _.size)
      def numberOfParamsToAdd(idx: Int) = cumulSize(cumulSize.indexWhere(_ > idx) - 1)

      for {
        jc @ (_u: JavaCallUsageInfo) <- usages
        call @ (_c: PsiMethodCallExpression) <- jc.getElement.toOption.map(_.getParent)
        exprs = call.getArgumentList.getExpressions
        (defaultArg @ (_d: PsiMethodCallExpression), idx) <- exprs.zipWithIndex
        if defaultArg.getText.contains("$default$")
      } {
        val exprsToAdd = exprs.take(numberOfParamsToAdd(idx))
        val text = defaultArg.getMethodExpression.getText + exprsToAdd.map(_.getText).mkString("(", ", ", ")")
        val newDefaultArg = JavaPsiFacade.getElementFactory(call.getProject).createExpressionFromText(text, defaultArg.getContext)
        defaultArg.replace(newDefaultArg)
      }
    }

    if (!UsageUtil.scalaUsage(usageInfo)) {
      if (isLastUsage) {
        updateNamedElements()
        addArgumentsToDefaultParamInJava()
      }
      return false
    }

    if (beforeMethodChange) {
      usageInfo match {
        case namedInfo: ScalaNamedElementUsageInfo =>
          processNamedElementUsage(changeInfo, namedInfo)
        case paramInfo: ParameterUsageInfo =>
          handleParametersUsage(changeInfo, paramInfo)
        case anonFunUsage: AnonFunUsageInfo =>
          handleAnonFunUsage(changeInfo, anonFunUsage)
        case _ =>
          processSimpleUsage(changeInfo, usageInfo)
      }
    }

    if (isLastUsage) {
      updateNamedElements()
      addArgumentsToDefaultParamInJava()
    }
    true
  }

  override def findConflicts(info: ChangeInfo,
                             refUsages: Ref[Array[UsageInfo]]): MultiMap[PsiElement, String] = {
    val usages = refUsages.get()
    val result = new MultiMap[PsiElement, String]()

    usages.foreach {
      case ScalaNamedElementUsageInfo(u: OverriderClassParamUsageInfo) => ConflictsUtil.addClassParameterConflicts(u.namedElement, info, result)
      case ScalaNamedElementUsageInfo(u: OverriderValUsageInfo) => ConflictsUtil.addBindingPatternConflicts(u.namedElement, info, result)
      case javaOverriderUsage: OverriderUsageInfo => ConflictsUtil.addJavaOverriderConflicts(javaOverriderUsage, info, result)
      case p: PatternUsageInfo => ConflictsUtil.addUnapplyUsagesConflicts(p, info, result)
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

  private def processSimpleUsage(change: ChangeInfo, usage: UsageInfo): Unit = {
    handleChangedName(change, usage)
    handleUsageArguments(change, usage)
  }

  private def processNamedElementUsage(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Unit = {
    usage.namedElement match {
      case fun: ScFunction if fun.isConstructor =>
        handleVisibility(change, usage)
        handleChangedParameters(change, usage)
      case fun: ScFunction if fun.isSynthetic =>
      case _: ScClass =>
        handleVisibility(change, usage)
        handleChangedParameters(change, usage)
      case _ =>
        handleVisibility(change, usage)
        handleChangedName(change, usage.asInstanceOf[UsageInfo])
        handleReturnTypeChange(change, usage)
        handleChangedParameters(change, usage)
    }
  }

  private def findMethodRefUsages(named: PsiNamedElement, results: ArrayBuffer[UsageInfo], searchInJava: Boolean): Unit = {
    val process = { ref: PsiReference =>
        val refElem = ref.getElement
        refElem match {
          case isAnonFunUsage(anonFunUsageInfo) => results += anonFunUsageInfo
          case (scRef: ScReferenceElement) childOf(_: ScImportSelector | _: ScImportExpr) => results += ImportUsageInfo(scRef)
          case (refExpr: ScReferenceExpression) childOf (mc: ScMethodCall) => results += MethodCallUsageInfo(refExpr, fullCall(mc))
          case ChildOf(infix @ ScInfixExpr(_, `refElem`, _)) => results += InfixExprUsageInfo(infix)
          case ChildOf(postfix @ ScPostfixExpr(_, `refElem`)) => results += PostfixExprUsageInfo(postfix)
          case ref @ ScConstructor.byReference(constr) => results += ConstructorUsageInfo(ref, constr)
          case refExpr: ScReferenceExpression => results += RefExpressionUsage(refExpr)
          case ChildOf(cp: ScConstructorPattern) if cp.ref == refElem => results += ConstructorPatternUsageInfo(cp)
          case ChildOf(ip: ScInfixPattern) if ip.operation == refElem => results += InfixPatternUsageInfo(ip)
          case ref: PsiReferenceExpression if searchInJava => results += new JavaCallUsageInfo(ref, true, false)
          case _ =>
        }
        true
    }

    ReferencesSearch.search(named, named.getUseScope).forEach(process)
  }

  @tailrec
  private def fullCall(mc: ScMethodCall): ScMethodCall = {
    mc.getParent match {
      case p: ScMethodCall if !mc.isApplyOrUpdateCall => fullCall(p)
      case _ => mc
    }
  }

  private def findParameterUsages(changeInfo: JavaChangeInfo,
                                  method: PsiMethod,
                                  results: ArrayBuffer[UsageInfo]): Unit = {
    for {
      paramInfo <- changeInfo.getNewParameters
      oldIdx = paramInfo.getOldIndex
      if oldIdx >= 0
      oldName = changeInfo.getOldParameterNames()(oldIdx)
      parameters = method.parameters
      if parameters.length > oldIdx
      param = parameters(oldIdx)
      newName = paramInfo.getName
      if oldName == param.name /*skip overriders with other param name*/ && newName != param.name
    } {
      addParameterUsages(param, oldIdx, newName, results)
    }
  }

  private def addParameterUsages(param: PsiParameter, oldIndex: Int, newName: String, results: ArrayBuffer[UsageInfo]) {
    val scope: SearchScope = param.getUseScope
    val process = (ref: PsiReference) => {
      ref.getElement match {
        case refElem: ScReferenceElement =>
          results += ParameterUsageInfo(oldIndex, newName, refElem)
        case refElem: PsiReferenceExpression =>
          results += new ChangeSignatureParameterUsageInfo(refElem, param.name, newName)
        case _ =>
      }
      true
    }
    ReferencesSearch.search(param, scope, false).forEach(process)
  }
}
