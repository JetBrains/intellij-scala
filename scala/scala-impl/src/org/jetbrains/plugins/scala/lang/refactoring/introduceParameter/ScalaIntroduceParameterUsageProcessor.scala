package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.refactoring.changeSignature.{ChangeInfo, ChangeSignatureUsageProcessor}
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.rename.ResolveSnapshotProvider.ResolveSnapshot
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import java.util
import scala.collection.immutable.ArraySeq

class ScalaIntroduceParameterUsageProcessor extends ChangeSignatureUsageProcessor {

  override def findUsages(info: ChangeInfo): Array[UsageInfo] = info match {
    case isIntroduceParameter(data) =>
      def toUsageInfo(range: TextRange) = {
        val file = data.methodToSearchFor.getContainingFile
        val doc = PsiDocumentManager.getInstance(data.getProject).getDocument(file)
        TextRangeUsageInfo(file, doc.createRangeMarker(range))
      }

      val occurrences = if (data.replaceAll) data.occurrences else Seq(data.mainOcc)
      occurrences.map(toUsageInfo).toArray
    case _ => Array.empty
  }

  override def processUsage(changeInfo: ChangeInfo, usageInfo: UsageInfo, beforeMethodChange: Boolean, usages: Array[UsageInfo]): Boolean = {
    if (!beforeMethodChange) return false

    changeInfo match {
      case isIntroduceParameter(data) =>
        val textRangeUsages = usages.iterator
          .collect { case t: TextRangeUsageInfo => t }
          .to(ArraySeq)
        if (textRangeUsages.headOption.forall(_.processed)) return false

        val pName = data.paramName
        val args = data.functionalArgParams.getOrElse("")
        val text = s"$pName$args"
        val file = textRangeUsages.head.file

        val manager = PsiDocumentManager.getInstance(file.getProject)
        manager.doPostponedOperationsAndUnblockDocument(manager.getDocument(file))

        ScalaRefactoringUtil.replaceOccurrences(textRangeUsages.map(usage => TextRange.create(usage.range)), text, file)
        textRangeUsages.foreach(_.processed = true)
        true
      case _ => false
    }
  }

  override def processPrimaryMethod(changeInfo: ChangeInfo): Boolean = false

  override def shouldPreviewUsages(changeInfo: ChangeInfo, usages: Array[UsageInfo]): Boolean = false

  override def findConflicts(info: ChangeInfo, refUsages: Ref[Array[UsageInfo]]): MultiMap[PsiElement, String] = new MultiMap[PsiElement, String]()

  override def registerConflictResolvers(snapshots: util.List[_ >: ResolveSnapshot],
                                         resolveSnapshotProvider: ResolveSnapshotProvider,
                                         usages: Array[UsageInfo],
                                         changeInfo: ChangeInfo): Unit = {}

  override def setupDefaultValues(changeInfo: ChangeInfo, refUsages: Ref[Array[UsageInfo]], project: Project): Boolean = true

}

private case class TextRangeUsageInfo(file: PsiFile, range: RangeMarker) extends UsageInfo(file, range.getStartOffset, range.getEndOffset) {
  var processed = false
}