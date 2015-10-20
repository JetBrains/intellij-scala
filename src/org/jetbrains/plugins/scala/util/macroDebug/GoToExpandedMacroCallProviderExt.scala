package org.jetbrains.plugins.scala
package util.macroDebug

import java.awt.event.MouseEvent
import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon._
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.util.Function
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.MacroExpandAction

import scala.collection.JavaConversions._

/**
 * User: Dmitry Naydanov
 * Date: 11/7/12
 */
class GoToExpandedMacroCallProviderExt extends LineMarkerProvider {

  def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = null

  override def collectSlowLineMarkers(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) = {
    val expansions = elements.map(p => (p, p.getCopyableUserData(MacroExpandAction.EXPANDED_KEY))).filter(_._2 != null)

    val res = expansions.map { case (current, saved) =>
      new RelatedItemLineMarkerInfo[PsiElement](current, current.getTextRange, Icons.NO_SCALA_SDK, Pass.UPDATE_OVERRIDEN_MARKERS,
        new Function[PsiElement, String] {
          def fun(param: PsiElement): String = "Undo Macro Expansion"
        },
        new GutterIconNavigationHandler[PsiElement] {
          def navigate(mouseEvent: MouseEvent, elt: PsiElement) = {
            inWriteAction {
              val newPsi = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(saved, PsiManager.getInstance(current.getProject))
              current.replace(newPsi)
              saved
            }
          }
        },
        GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]())
    }
    result.addAll(res)
  }

  def collectSlowLineMarkersOld(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) {
    ScalaMacroDebuggingUtil.allMacroCalls.clear()

    if (!ScalaMacroDebuggingUtil.isEnabled || elements.isEmpty) return
    val first = elements get 0
    val file = first.getContainingFile

    val synFile = file match {
      case scalaFile: ScalaFile if ScalaMacroDebuggingUtil tryToLoad scalaFile => Some(scalaFile)
      case _ => None
    }

    val macrosFound = elements filter ScalaMacroDebuggingUtil.isMacroCall
    if (macrosFound.isEmpty) return

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
