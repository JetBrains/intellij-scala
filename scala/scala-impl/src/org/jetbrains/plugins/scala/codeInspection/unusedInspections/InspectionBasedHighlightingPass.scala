package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import java.util.Collections

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.lang.annotation.{Annotation, HighlightSeverity}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInspection.suppression.ScalaInspectionSuppressor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.jdk.CollectionConverters._
import scala.collection.mutable

abstract class InspectionBasedHighlightingPass(file: ScalaFile, document: Option[Document], inspection: HighlightingPassInspection)
  extends TextEditorHighlightingPass(file.getProject, document.orNull, /*runIntentionPassAfter*/ false) {

  private val highlightInfos = mutable.Buffer[HighlightInfo]()

  private val inspectionSuppressor = new ScalaInspectionSuppressor

  private def profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile

  def isEnabled(element: PsiElement): Boolean = {
    profile.isToolEnabled(highlightKey, element) && !inspectionSuppressor.isSuppressedFor(element, inspection.getShortName)
  }

  def severity: HighlightSeverity = {
    Option(highlightKey).map {
      profile.getErrorLevel(_, file).getSeverity
    }.getOrElse(HighlightSeverity.WEAK_WARNING)
  }

  def highlightKey: HighlightDisplayKey = HighlightDisplayKey.find(inspection.getShortName)

  override def doCollectInformation(progress: ProgressIndicator): Unit = {
    if (shouldHighlightFile) {
      highlightInfos.clear()
      processFile()
    }
  }

  private def shouldHighlightFile: Boolean = HighlightingLevelManager.getInstance(file.getProject).shouldInspect(file)

  override def doApplyInformationToEditor(): Unit = {
    if (shouldHighlightFile) {
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, getDocument, 0, file.getTextLength,
        highlightInfos.asJavaCollection, getColorsScheme, getId)
    } else {
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, getDocument, 0, file.getTextLength,
        Collections.emptyList(), getColorsScheme, getId)
    }
  }

  private def processFile(): Unit = {
    if (isEnabled(file)) {
      val infos: Iterator[ProblemInfo] = file.depthFirst().filter {
        inspection.shouldProcessElement
      }.filter {
        isEnabled
      }.flatMap {
        inspection.invoke(_, isOnTheFly = true)
      }
      highlightInfos ++= infos.map { info =>
        val range = info.element.getTextRange
        val annotation = new Annotation(range.getStartOffset, range.getEndOffset, severity, info.message, info.message)
        annotation.setHighlightType(info.highlightingType)
        info.fixes.foreach(annotation.registerFix(_, range, highlightKey))
        HighlightInfo.fromAnnotation(annotation)
      }
    }
  }

  override def getInfos: java.util.List[HighlightInfo] = highlightInfos.asJava
}
