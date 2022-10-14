package org.jetbrains.plugins.scala
package codeInspection
package declarationRedundancy

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo.convertSeverity
import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInspection.suppression.ScalaInspectionSuppressor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.util.Collections
import scala.collection.mutable
import scala.jdk.CollectionConverters._

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
        val infoType = toHighlightInfoType(info.highlightingType, severity)
        val highlightInfo = HighlightInfo.newHighlightInfo(infoType)
          .range(range)
          .descriptionAndTooltip(info.message)
          .create()
        info.fixes.foreach { action =>
          highlightInfo.registerFix(action, null, info.message, range, highlightKey)
        }

        highlightInfo
      }
    }
  }

  override def getInfos: java.util.List[HighlightInfo] = highlightInfos.asJava

  private def toHighlightInfoType(problemHighlightType: ProblemHighlightType, severity: HighlightSeverity): HighlightInfoType =
    problemHighlightType match {
      case ProblemHighlightType.LIKE_UNUSED_SYMBOL => HighlightInfoType.UNUSED_SYMBOL
      case ProblemHighlightType.LIKE_UNKNOWN_SYMBOL => HighlightInfoType.WRONG_REF
      case ProblemHighlightType.LIKE_DEPRECATED => HighlightInfoType.DEPRECATED
      case ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL => HighlightInfoType.MARKED_FOR_REMOVAL
      case ProblemHighlightType.POSSIBLE_PROBLEM => HighlightInfoType.POSSIBLE_PROBLEM
      case _ => convertSeverity(severity)
    }
}
