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
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.JavaConverters._
import scala.collection.mutable

abstract class ScalaInspectionBasedHighlightingPass(file: ScalaFile, document: Document) extends TextEditorHighlightingPass(file.getProject, document) {
  private val highlightInfos = mutable.Buffer[HighlightInfo]()

  private val inspectionSuppressor = new ScalaInspectionSuppressor

  val inspectionShortName: String

  def shouldProcessElement(elem: PsiElement): Boolean

  def processElement(elem: PsiElement): Seq[Annotation]

  private def profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile

  def isEnabled(element: PsiElement): Boolean = {
    profile.isToolEnabled(highlightKey, element) && !inspectionSuppressor.isSuppressedFor(element, inspectionShortName)
  }

  def severity: HighlightSeverity = {
    Option(highlightKey).map {
      profile.getErrorLevel(_, file).getSeverity
    }.getOrElse(HighlightSeverity.WEAK_WARNING)
  }

  def highlightKey: HighlightDisplayKey = HighlightDisplayKey.find(inspectionShortName)

  override def doCollectInformation(progress: ProgressIndicator): Unit = {
    if (shouldHighlightFile) {
      highlightInfos.clear()
      processFile()
    }
  }

  private def shouldHighlightFile: Boolean = HighlightingLevelManager.getInstance(file.getProject).shouldInspect(file)

  override def doApplyInformationToEditor() {
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
      val annotations = file.depthFirst.filter(shouldProcessElement).map(processElement).flatten
      highlightInfos ++= annotations.map(HighlightInfo.fromAnnotation)
    }
  }

  override def getInfos: java.util.List[HighlightInfo] = highlightInfos.asJava
}
