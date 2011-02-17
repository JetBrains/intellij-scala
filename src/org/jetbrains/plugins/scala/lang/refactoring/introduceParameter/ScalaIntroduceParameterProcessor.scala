package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import com.intellij.refactoring.BaseRefactoringProcessor
import java.lang.String
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.{MethodReferencesSearch, OverridingMethodsSearch}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiReference, PsiMethod}
import com.intellij.refactoring.util.usageInfo.{NoConstructorClassUsageInfo, DefaultConstructorImplicitUsageInfo}
import com.intellij.usageView.{UsageViewUtil, UsageViewDescriptor, UsageInfo}
import com.intellij.refactoring.introduceParameter.{InternalUsageInfo, ExternalUsageInfo}
import com.intellij.refactoring.util.occurences.{ExpressionOccurenceManager, LocalVariableOccurenceManager, OccurenceManager}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.util.TextRange
import collection.mutable.{ArrayBuffer, ArrayBuilder}
import java.util.{Comparator, Arrays}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaIntroduceParameterProcessor(project: Project, methodToSearchFor: PsiMethod,
                                       function: ScFunctionDefinition, replaceAllOccurences: Boolean,
                                       occurrences: Array[TextRange], startOffset: Int, endOffset: Int)
        extends BaseRefactoringProcessor(project) {
  def getCommandName: String = "Introduce Parameter"

  def performRefactoring(usages: Array[UsageInfo]): Unit = {
    Arrays.sort(usages, new Comparator[UsageInfo] {
      def compare(o1: UsageInfo, o2: UsageInfo): Int = o1.startOffset - o2.startOffset
    })
  }

  def findUsages: Array[UsageInfo] = {
    val result: ArrayBuffer[UsageInfo] = new ArrayBuffer[UsageInfo]
    val overridingMethods: Array[PsiMethod] =
      OverridingMethodsSearch.search(methodToSearchFor, methodToSearchFor.getUseScope, true).
        toArray(PsiMethod.EMPTY_ARRAY)
    result += new UsageInfo(methodToSearchFor)
    for (overridingMethod <- overridingMethods) {
      result += new UsageInfo(overridingMethod)
    }
    val refs: Array[PsiReference] =
      MethodReferencesSearch.search(methodToSearchFor, GlobalSearchScope.projectScope(myProject), true).
        toArray(PsiReference.EMPTY_ARRAY)
    for (refa <- refs; ref = refa.getElement) {
      if (ref.isInstanceOf[PsiMethod] && (ref.asInstanceOf[PsiMethod]).isConstructor) {
        val implicitUsageInfo: DefaultConstructorImplicitUsageInfo =
          new DefaultConstructorImplicitUsageInfo(ref.asInstanceOf[PsiMethod],
            (ref.asInstanceOf[PsiMethod]).getContainingClass, methodToSearchFor)
        result += implicitUsageInfo
      }
      else if (ref.isInstanceOf[PsiClass]) {
        result += new NoConstructorClassUsageInfo(ref.asInstanceOf[PsiClass])
      }
      else if (!PsiTreeUtil.isAncestor(function, ref, true)) {
        result += new ExternalUsageInfo(ref)
      }
      else {
        result += new UsageInfo(ref)
      }
    }
    val file = function.getContainingFile
    if (replaceAllOccurences) {
      for (occurence <- occurrences) {
        val start = occurence.getStartOffset
        val end = occurence.getEndOffset
        result += new UsageInfo(file, start, end)
      }
    }
    else {
      result += new UsageInfo(file, startOffset, endOffset)
    }
    val usageInfos: Array[UsageInfo] = result.toArray
    return UsageViewUtil.removeDuplicatedUsages(usageInfos)
  }

  def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor = {
    new ScalaIntroduceParameterViewDescriptor(methodToSearchFor)
  }
}