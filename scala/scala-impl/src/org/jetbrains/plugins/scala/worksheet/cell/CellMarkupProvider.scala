package org.jetbrains.plugins.scala.worksheet.cell

import java.util

import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.PsiElement

/**
  * User: Dmitry.Naydanov
  */
class CellMarkupProvider extends LineMarkerProvider {
  override def getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    if (!CellManager.getInstance(psiElement.getProject).processProbablyStartElement(psiElement)) return null
    LineMarkersPass.createMethodSeparatorLineMarker(psiElement, EditorColorsManager.getInstance())
  }

  override def collectSlowLineMarkers(list: util.List[PsiElement], 
                                      collection: util.Collection[LineMarkerInfo[_ <: PsiElement]]): Unit = {}
}
