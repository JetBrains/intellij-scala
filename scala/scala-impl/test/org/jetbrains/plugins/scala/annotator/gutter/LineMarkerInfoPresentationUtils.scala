package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement

object LineMarkerInfoPresentationUtils {

  def describeLineMarker(info: LineMarkerInfo[_]): String = {
    val elementText = info.getElement.asInstanceOf[PsiElement].getText
    val tooltipText = info.getLineMarkerTooltip
    s"$elementText (icon: ${info.getIcon}, tooltip: $tooltipText)"
  }

  def describeLineMarkerWithRange(info: LineMarkerInfo[_]): String =
    s"(${info.startOffset},${info.endOffset}) -> ${describeLineMarker(info)}"

  def describeLineWithLine(lineNumber: Int, info: LineMarkerInfo[_]): String =
    s"$lineNumber -> ${describeLineMarker(info)}"

  def buildLineNumbersText(infos: Seq[LineMarkerInfo[_]], document: Document): String = {
    val lines = infos.map(_.startOffset).map(document.getLineNumber)
    lines.mkString(", ")
  }
}
