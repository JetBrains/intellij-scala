package org.jetbrains.plugins.scala
package util.macroDebug

import java.awt.event.MouseEvent
import java.util
import com.intellij.psi._
import lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.icons.Icons

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon._
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import collection.GenIterable
import scala.Some
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, MethodInvocation}
import com.intellij.psi.codeStyle.CodeStyleManager
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.{NavigatablePsiElement, PsiDocumentManager, PsiElement}
import com.intellij.util.Function
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.GenIterable

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class GoToExpandedMacroCallProviderExt extends LineMarkerProvider {

  def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = null

  def collectSlowLineMarkers(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) {
    ScalaMacroDebuggingUtil.allMacroCalls.clear()

    if (!ScalaMacroDebuggingUtil.isEnabled || elements.isEmpty) return
    val first = elements get 0
    val file = first.getContainingFile

    val synFile = file match {
      case scalaFile: ScalaFile if ScalaMacroDebuggingUtil tryToLoad scalaFile => Some(scalaFile)
      case _ => None
    }

    import scala.collection.JavaConversions._
    val macrosFound = elements filter ScalaMacroDebuggingUtil.isMacroCall
    if (macrosFound.length == 0) return

    macrosFound foreach {
      case macroCall =>
        val markerInfo = new RelatedItemLineMarkerInfo[PsiElement](macroCall, macroCall.getTextRange, Icons.NO_SCALA_SDK,
          Pass.UPDATE_OVERRIDEN_MARKERS, new Function[PsiElement, String] {
            def fun(param: PsiElement): String = {
              if (!ScalaMacroDebuggingUtil.macrosToExpand.contains(macroCall)) {
                "Expand macro"
              } else {
                "Collapse macro"
              }
            }
          },
          new GutterIconNavigationHandler[PsiElement] {
            def navigate(mouseEvent: MouseEvent, elt: PsiElement) {
              if (ScalaMacroDebuggingUtil.macrosToExpand.contains(elt)) {
                ScalaMacroDebuggingUtil.macrosToExpand.remove(elt)
              } else {
                ScalaMacroDebuggingUtil.macrosToExpand.add(elt)
              }
              ScalaMacroDebuggingUtil.expandMacros(elt.getProject)
            }
          }, GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]())

        result add markerInfo
        ScalaMacroDebuggingUtil.allMacroCalls.add(macroCall)
    }
  }
}
