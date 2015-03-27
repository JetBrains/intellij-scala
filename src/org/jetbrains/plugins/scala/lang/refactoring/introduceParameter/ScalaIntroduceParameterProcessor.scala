package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import java.util
import java.util.Comparator

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.{MethodReferencesSearch, OverridingMethodsSearch}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.introduceParameter.{IntroduceParameterData, IntroduceParameterMethodUsagesProcessor, JavaExpressionWrapper}
import com.intellij.usageView.{UsageInfo, UsageViewDescriptor, UsageViewUtil}
import gnu.trove.TIntArrayList
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaIntroduceParameterProcessor(project: Project, editor: Editor, methodToSearchFor: PsiMethod,
                                       function: ScFunctionDefinition, replaceAllOccurences: Boolean,
                                       occurrences: Array[TextRange], startOffset: Int, endOffset: Int,
                                       paramName: String, isDefaultParam: Boolean, tp: ScType, expression: ScExpression)
        extends BaseRefactoringProcessor(project) with IntroduceParameterData {
  private val document = editor.getDocument
  private val file = function.getContainingFile

  val (hasDefaults, hasRep, posNumber) = {
    val clauses = function.paramClauses.clauses
    if (clauses.length == 0) (false, false, 0)
    else {
      val hasDef = clauses.apply(0).parameters.exists(p => p.isDefaultParam)
      val hasRep = clauses.apply(0).parameters.exists(p => p.isRepeatedParameter)
      val num = clauses.apply(0).parameters.length - (if (hasRep) 1 else 0)
      (hasDef, hasRep, num)
    }
  }

  private def getRangeElementOrFile(range: TextRange): PsiElement = {
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset - 1)
    var element = PsiTreeUtil.findCommonParent(startElement, endElement)
    while (element.getTextRange.getStartOffset >= range.getStartOffset &&
           element.getTextRange.getEndOffset   <= range.getEndOffset) {
      if (element.getTextRange.equals(range) && element.isInstanceOf[ScExpression]) return element
      element = element.getParent
    }
    file
  }

  private def changeMethodSignatureAndResolveFieldConflicts(usage: UsageInfo, usages: Array[UsageInfo]) {
    for (processor <- IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions) {
      if (!processor.processChangeMethodSignature(this, usage, usages)) return
    }
  }

  private def changeExternalUsage(usage: UsageInfo, usages: Array[UsageInfo]) {
    for (processor <- IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions) {
      if (!processor.processChangeMethodUsage(this, usage, usages)) return
    }
  }

  private def addDefaultConstructor(usage: UsageInfo, usages: Array[UsageInfo]) {
    for (processor <- IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions) {
      if (!processor.processAddDefaultConstructor(this, usage, usages)) return
    }
  }

  private def addSuperCall(usage: UsageInfo, usages: Array[UsageInfo]) {
    for (processor <- IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions) {
      if (!processor.processAddSuperCall(this, usage, usages)) return
    }
  }

  private case class MethodUsageInfo(ref: PsiElement) extends UsageInfo(ref)
  private case class FileRangeUsageInfo(file: PsiFile, range: TextRange)
    extends UsageInfo(file, range.getStartOffset, range.getEndOffset)
  private case class ElementRangeUsageInfo(element: PsiElement, range: TextRange)
    extends UsageInfo(element)
  private case class IPUsageInfo(elem: PsiMethod) extends UsageInfo(elem)

  def getCommandName: String = "Introduce Parameter"

  def performRefactoring(usages: Array[UsageInfo]) {
    val sortedUsages = util.Arrays.copyOf(usages, usages.length)
    util.Arrays.sort(sortedUsages, new Comparator[UsageInfo] {
      def compare(o1: UsageInfo, o2: UsageInfo): Int =
        if (o1.getRangeInElement.getStartOffset != o2.getRangeInElement.getStartOffset)
          o1.getRangeInElement.getStartOffset - o2.getRangeInElement.getStartOffset
        else o1.getRangeInElement.getEndOffset - o2.getRangeInElement.getEndOffset
    })
    val iter = sortedUsages.reverseIterator
    while (iter.hasNext) {
      val usage = iter.next()
      usage match {
        case IPUsageInfo(method) =>
          changeMethodSignatureAndResolveFieldConflicts(usage, usages)
        case MethodUsageInfo(ref) =>
          ref match {
            case clazz: PsiClass =>
              addDefaultConstructor(usage, usages)
            case method: PsiMethod if method.isConstructor =>
              addSuperCall(usage, usages)
            case _ =>
              changeExternalUsage(usage, usages)
          }
        case ElementRangeUsageInfo(element, range) =>
          element match {
            case expr: ScExpression =>
              val refExpr = ScalaPsiElementFactory.createExpressionFromText(paramName, element.getManager)
              expr.replaceExpression(refExpr, removeParenthesis = true)
            case _ =>
              ScalaRefactoringUtil.replaceOccurence(range, paramName, file, editor)
          }
        case FileRangeUsageInfo(psiFile, range) =>
          ScalaRefactoringUtil.replaceOccurence(range, paramName, psiFile, editor)
      }
    }
  }

  def findUsages: Array[UsageInfo] = {
    val result: ArrayBuffer[UsageInfo] = new ArrayBuffer[UsageInfo]
    val overridingMethods: Array[PsiMethod] =
      OverridingMethodsSearch.search(methodToSearchFor, methodToSearchFor.getUseScope, true).
        toArray(PsiMethod.EMPTY_ARRAY)
    result += IPUsageInfo(methodToSearchFor)
    for (overridingMethod <- overridingMethods) {
      result += IPUsageInfo(overridingMethod)
    }
    val refs: Array[PsiReference] =
      MethodReferencesSearch.search(methodToSearchFor, GlobalSearchScope.projectScope(myProject), true).
        toArray(PsiReference.EMPTY_ARRAY)
    for (refa <- refs; ref = refa.getElement) {
      result += MethodUsageInfo(ref)
    }
    if (replaceAllOccurences) {
      for (occurrence <- occurrences) {
        val element = getRangeElementOrFile(occurrence)
        element match {
          case file: PsiFile => result += FileRangeUsageInfo(file, occurrence)
          case _ => result += ElementRangeUsageInfo(element, occurrence)
        }
      }
    }
    else {
      val range = new TextRange(startOffset, endOffset)
      val element = getRangeElementOrFile(range)
      element match {
        case file: PsiFile => result += FileRangeUsageInfo(file, range)
        case _ => result += ElementRangeUsageInfo(element, range)
      }
    }
    val usageInfos: Array[UsageInfo] = result.toArray
    UsageViewUtil.removeDuplicatedUsages(usageInfos)
  }

  def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor = {
    new ScalaIntroduceParameterViewDescriptor(methodToSearchFor)
  }

  def getParametersToRemove: TIntArrayList = new TIntArrayList() //todo:

  def getForcedType: PsiType = ScType.toPsi(tp, project, function.getResolveScope)

  def getScalaForcedType: ScType = tp

  def isGenerateDelegate: Boolean = false //todo:?

  def isDeclareFinal: Boolean = false //todo:?

  def isDeclareDefault: Boolean = isDefaultParam

  def getReplaceFieldsWithGetters: Int = 0 //todo:

  def isReplaceAllOccurences: Boolean = replaceAllOccurences

  def getParameterName: String = paramName

  def isRemoveLocalVariable: Boolean = false //todo:

  def getLocalVariable: PsiLocalVariable = null //todo:

  def getScalaExpressionToSearch: ScExpression = expression

  def getExpressionToSearch: PsiExpression =
    JavaPsiFacade.getElementFactory(function.getProject).createExpressionFromText(getParameterName, expression.getContext)

  def getParameterInitializer =
    new JavaExpressionWrapper(
      JavaPsiFacade.getElementFactory(function.getProject).createExpressionFromText(getParameterName, expression.getContext)
    )

  def getMethodToSearchFor: PsiMethod = methodToSearchFor

  def getMethodToReplaceIn: PsiMethod = function

  def getProject: Project = project
}