package org.jetbrains.plugins.scala.worksheet.runconfiguration

import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiDocumentManager, PsiElement, PsiWhiteSpace}
import com.intellij.util.NullableFunction
import org.jetbrains.plugins.scala.extensions.implementation.iterator.PrevSiblignsIterator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

/**
  * User: Dmitry.Naydanov
  * Date: 27.02.17.
  */
class WorksheetLineMarkerProvider extends LineMarkerProvider {
  override def getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    psiElement match {
      case empty: PsiElement if empty.getTextRange.isEmpty => null
      case _: PsiWhiteSpace | _: PsiComment => null
      case _ =>
        val project = psiElement.getProject
        
        Option(PsiDocumentManager.getInstance(project).getCachedDocument(psiElement.getContainingFile)).flatMap {
          document => EditorFactory.getInstance().getEditors(document, project).headOption
        }
        
        def marker(scalaFile: ScalaFile, checkParent: Boolean = false) = {
          Option(PsiDocumentManager.getInstance(project).getCachedDocument(psiElement.getContainingFile)).flatMap {
            document => EditorFactory.getInstance().getEditors(document, project).headOption.map(e => (e, document))
          }.flatMap {
            case (editor, document) =>
              @inline def getLineStartOffset(el: PsiElement) = document.getLineNumber(el.getTextRange.getStartOffset)

              if (checkParent && (getLineStartOffset(psiElement) == getLineStartOffset(psiElement.getParent))) None
              else WorksheetCache.getInstance(scalaFile.getProject).getLastProcessedIncremental(editor).filter(
                _ == getLineStartOffset(psiElement)).map(
                _ => createArrowMarker(psiElement)
              )
          }.orNull
        }
        
        def testElement(el: PsiElement): Boolean = el.getParent.isInstanceOf[ScalaFile]

        def checkIfNotTop(): Boolean = psiElement.getPrevSibling match {
          case null => true
          case some =>
            !new PrevSiblignsIterator(some).exists {
              case _: PsiComment | _: PsiWhiteSpace => false
              case empt if empt.getTextRange.isEmpty => false
              case _ => true
            }
        }
        
        psiElement.getContainingFile match {
          case scalaFile: ScalaFile if scalaFile.isWorksheetFile && WorksheetFileSettings.isRepl(scalaFile) =>
            if (testElement(psiElement)) marker(scalaFile) else 
              if (testElement(psiElement.getParent) && checkIfNotTop()) marker(scalaFile, checkParent = true) else null
          case _ => null
        }
    }
  }

  override def collectSlowLineMarkers(list: util.List[PsiElement],
                                      collection: util.Collection[LineMarkerInfo[_ <: PsiElement]]): Unit = {}

  private def createArrowMarker(psiElement: PsiElement) = {
    val leaf = Option(PsiTreeUtil.firstChild(psiElement)).getOrElse(psiElement)
    new LineMarkerInfo[PsiElement](leaf, leaf.getTextRange, AllIcons.Actions.Forward, Pass.LINE_MARKERS,
      NullableFunction.NULL.asInstanceOf[com.intellij.util.Function[PsiElement, String]], null, GutterIconRenderer.Alignment.RIGHT)
  }
}