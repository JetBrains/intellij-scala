package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiFile, PsiReference}
import com.intellij.refactoring.{BaseRefactoringProcessor, RefactoringBundle}
import com.intellij.usageView.{UsageInfo, UsageViewBundle, UsageViewDescriptor, UsageViewUtil}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{&, inReadAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInliner.InlineState
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import java.util.{Collections => JCollections, Set => JSet}
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.IterableHasAsScala

abstract class ScalaInlineProcessor(
  element: ScalaPsiElement,
  reference: Option[PsiReference],
  inlineThisOnly: Boolean,
  shouldRemoveDefinition: Boolean,
)(
  implicit project: Project
) extends BaseRefactoringProcessor(project) {

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
    if (inlineThisOnly) return reference.map(new UsageInfo(_)).toArray

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

  /**
   * This mutable state is supposed to be initialised once inside [[performRefactoring]]
   * and used once inside [[performPsiSpoilingRefactoring]]
   *
   * If the original target string used margins, it should continue using margins after we inline some injections.<br>
   * If the original target string didn't use margins, new string should also not use them (we assume that it was intentional that there were no margins)
   *
   * To achieve this, we need to collect all modified target string literals.<br>
   * The issue is that [[org.jetbrains.plugins.scala.format.InterpolatedStringFormatter.format]]
   * which is used in [[ScalaInliner]] modifies the original string and  removes the margins.
   * So we need to add them back using [[MultilineStringUtil$.addMarginsAndFormatMLString]] if needed.
   *
   * The best place to do it according to JavaDoc is [[performPsiSpoilingRefactoring]],
   * thus we have to use this shared mutable state
   */
  private var modifiedMultilineStrings: InlineState = InlineState.empty

  override def performRefactoring(usages: Array[UsageInfo]): Unit = {
    //sorting mainly for predictability and reproducibility
    val usagesInReverseOrder = usages
      .groupBy(_.getElement.getContainingFile)
      .view.mapValues(_.sortBy(_.getElement.getTextRange.getStartOffset))
      .toSeq
      .flatMap(_._2)

    val scalaInliner = new ScalaInliner
    modifiedMultilineStrings = scalaInliner.inlineUsages(usagesInReverseOrder, element)

    if (shouldRemoveDefinition && !inlineThisOnly) {
      removeDefinition()
    }
  }

  /**
   * @todo highlight inlined usages, like in Java.<br>
   *       You might look into:
   *       - [[org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.highlightOccurrences]]
   *       - [[com.intellij.refactoring.inline.InlineLocalHandler.highlightOccurrences]]
   */
  override def performPsiSpoilingRefactoring(): Unit = {
    val stringLiteralsToAdjust: Seq[(ScStringLiteral, Option[Char])] =
      modifiedMultilineStrings.newStringLiteralToOriginalMarginChar.toSeq

    modifiedMultilineStrings = InlineState.empty

    //using distinct files to minimise usages of inReadAction &  getDocument
    val fileToDocument: Map[PsiFile, Document] = stringLiteralsToAdjust.map(_._1.getContainingFile).distinct.flatMap { file =>
      val vFile = file.getViewProvider.getVirtualFile
      val document = inReadAction(FileDocumentManager.getInstance().getDocument(vFile))
      if (document == null)
        None
      else
        Some(file -> document)
    }.toMap

    //MultilineStringUtil.addMarginsAndFormatMLStringWithoutCheck operates with document and psi offsets.
    //After each invocation it modifies the original string literal and there is a mismatch between document & psi offsets
    //This could be fixed by committing the document.
    //However, to avoid calling the method too much, instead, we process string literals in reverse order
    val stringLiteralsInReverseOrder = stringLiteralsToAdjust.sortBy(_._1.getTextRange.getStartOffset).reverse
    stringLiteralsInReverseOrder.collect { case (multilineString, Some(marginChar)) =>
      val documentOpt = fileToDocument.get(multilineString.getContainingFile)
      documentOpt.foreach { document =>
        MultilineStringUtil.addMarginsAndFormatMLStringWithoutCheck(multilineString, document, marginChar, caretOffset = 0)
      }
    }
  }

  override def computeUnloadedModulesFromUseScope(descriptor: UsageViewDescriptor): JSet[UnloadedModuleDescription] =
    if (inlineThisOnly) JCollections.emptySet
    else super.computeUnloadedModulesFromUseScope(descriptor)

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
