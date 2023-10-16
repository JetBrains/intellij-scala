package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.{BaseRefactoringProcessor, RefactoringBundle}
import com.intellij.usageView.{UsageInfo, UsageViewBundle, UsageViewDescriptor, UsageViewUtil}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.&
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.IterableHasAsScala

abstract class ScalaInlineProcessor(element: ScalaPsiElement, protected val shouldRemoveDefinition: Boolean = true)
                                   (implicit project: Project) extends BaseRefactoringProcessor(project) {
  final protected val inliner: ScalaInliner = new ScalaInliner

  protected def removeDefinition(): Unit

  @Nls
  protected def processedElementsUsageViewHeader: String

  override def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor = new UsageViewDescriptor {
    override def getElements: Array[PsiElement] = Array(element)

    override def getCommentReferencesText(usagesCount: Int, filesCount: Int): String =
      RefactoringBundle.message("comments.elements.header", UsageViewBundle.getOccurencesString(usagesCount, filesCount))

    override def getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
      JavaRefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount))

    override def getProcessedElementsHeader: String = processedElementsUsageViewHeader
  }

  override def findUsages(): Array[UsageInfo] = {
    val usages = collection.mutable.HashSet.empty[UsageInfo]

    usages ++= ReferencesSearch.search(element, element.getUseScope).findAll().asScala.map(new UsageInfo(_))

    if (shouldRemoveDefinition) {
      element match {
        case (named: ScNamedElement) & inNameContext(member: ScMember) if member.isDefinedInClass =>
          usages ++= ScalaOverridingMemberSearcher.search(named).map(new UsageInfo(_))
        case _ =>
      }
    }

    UsageViewUtil.removeDuplicatedUsages(usages.toArray)
  }

  override def performRefactoring(usages: Array[UsageInfo]): Unit = {
    usages.foreach(inliner.inlineUsage(_, element))

    if (shouldRemoveDefinition)
      removeDefinition()
  }

  final protected def removeElementWithNonSignificantSiblings(value: PsiElement): Unit = {
    val children = new ArrayBuffer[PsiElement]
    var psiElement = value.getNextSibling
    while (psiElement != null && (psiElement.getNode.getElementType == ScalaTokenTypes.tSEMICOLON || psiElement.getText.trim == "")) {
      children += psiElement
      psiElement = psiElement.getNextSibling
    }
    for (child <- children) {
      child.getParent.getNode.removeChild(child.getNode)
    }
    ScalaPsiUtil.deleteElementKeepingComments(value)
  }
}
