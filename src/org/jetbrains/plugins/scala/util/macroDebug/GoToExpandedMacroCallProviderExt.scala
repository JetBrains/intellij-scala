package org.jetbrains.plugins.scala
package util.macroDebug

import java.awt.event.MouseEvent
import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon._
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
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
  private val errorMessage = "Synthetic source isn't available"

  def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = null 

  def collectSlowLineMarkers(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) {
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
    
    val nullOffsets: GenIterable[(Int, Int, Int)]  = Stream.iterate((-1,-1,-1))((t) => (t._1,t._2,t._3))
    val offsets: GenIterable[(Int, Int, Int)] = synFile map (ScalaMacroDebuggingUtil getOffsets _) map {
      case Some(o) if o.length == macrosFound.length => o
      case None => nullOffsets 
    } getOrElse nullOffsets 

    
    (0 /: (macrosFound zip offsets)) {
      case (offsetsSoFar, (macroCall, (length, start, end))) =>
        val off = if (length <= 0) {
          -1
        } else {
          start + offsetsSoFar - (if (ScalaMacroDebuggingUtil.needFixCarriageReturn) synFile map {
            PsiDocumentManager getInstance file.getProject getDocument _ getLineNumber start
          } getOrElse 0 else 0)
        }
        
        val markerInfo = new RelatedItemLineMarkerInfo[PsiElement](macroCall, macroCall.getTextRange, Icons.NO_SCALA_SDK,
        Pass.UPDATE_OVERRIDEN_MARKERS, new Function[PsiElement, String] {
            def fun(param: PsiElement): String = {

              if (off <= 0) {
                errorMessage
              } else {
                synFile map (ScalaMacroDebuggingUtil loadCode _) map {
                  file => PsiDocumentManager getInstance file.getProject getDocument file
                } map ( _ getText new TextRange(off, off + length) ) getOrElse errorMessage
              }
            }
          },
          new GutterIconNavigationHandler[PsiElement] {
            def navigate(mouseEvent: MouseEvent, elt: PsiElement) {
              if (off <= 0) return 
              var macroExpanded = ScalaMacroDebuggingUtil loadCode file findElementAt off
              while (macroExpanded != null && !macroExpanded.isInstanceOf[NavigatablePsiElement])
                macroExpanded = macroExpanded.getParent

              if (macroExpanded == null) return
              
              PsiElementListNavigator.openTargets(mouseEvent,
                Array[NavigatablePsiElement](macroExpanded.asInstanceOf[NavigatablePsiElement]),
                "GoTo Expanded", "GoTo Expanded" /* todo: please review */, new GotoFileCellRenderer(5))
            }
          }, GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]())

        result add markerInfo

        offsetsSoFar + length - (end - start)
    } 
  }
}
