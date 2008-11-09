package org.jetbrains.plugins.scala.annotator.gutter


import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{NavigatablePsiElement, PsiMethod, PsiElement, PsiClass}
import java.util.{Collection, List}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.statements.{ScFunction, ScFunctionDeclaration}
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait}
import lang.psi.types.FullSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.10.2008
 */

class ScalaLineMarkerProvider extends LineMarkerProvider {
  def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      val offset = element.getTextRange.getStartOffset
      element.getParent match {
        case method: ScFunction if method.getParent.isInstanceOf[ScTemplateBody] => {
          val signatures = (HashSet[FullSignature](method.superSignatures: _*)).toSeq
          val icon = if (GutterUtil.isOverrides(method)) GutterIcons.OVERRIDING_METHOD_ICON
                     else GutterIcons.IMPLEMENTING_METHOD_ICON
          val typez = ScalaMarkerType.OVERRIDING_MEMBER
          if (signatures.length > 0) {
            return new LineMarkerInfo[PsiElement](method, offset, icon, Pass.UPDATE_ALL,
              typez.fun, typez.handler, GutterIconRenderer.Alignment.LEFT)
          }
        }
        case _ =>
      }
    }
    return null
  }

  def collectSlowLineMarkers(elements: List[PsiElement], result: Collection[LineMarkerInfo[_ <: PsiElement]]) {
    ApplicationManager.getApplication().assertReadAccessAllowed()

    for (element <- elements.toArray) {
      ProgressManager.getInstance().checkCanceled()

      val methods = new ArrayBuffer[ScFunction]
      element match {
        case clazz: ScTypeDefinition => {
          GutterUtil.collectInheritingClasses(clazz, result)
        }
        case x: ScFunction => methods += x
        case _ =>
      }
    }
    //todo: process methods
  }
}

private object GutterUtil {
  def collectInheritingClasses(clazz: ScTypeDefinition, result: Collection[LineMarkerInfo[_ <: PsiElement]]) {
    if ("scala.ScalaObject".equals(clazz.getQualifiedName)) return

    val inheritor = ClassInheritorsSearch.search(clazz, false).findFirst
    if (inheritor != null) {
      val offset = clazz.getTextOffset
      val icon = clazz match {
        case _: ScTrait => GutterIcons.IMPLEMENTED_INTERFACE_MARKER_RENDERER
        case _ => GutterIcons.SUBCLASSED_CLASS_MARKER_RENDERER
      }
      val typez = MarkerType.SUBCLASSED_CLASS
      val info = new LineMarkerInfo[PsiClass](clazz, offset, icon, Pass.UPDATE_OVERRIDEN_MARKERS, typez.getTooltip[PsiClass], typez.getNavigationHandler[PsiClass])
      result.add(info)
    }
  }

  def isOverrides(element: PsiElement) = {
    element match {
      case method: PsiMethod => method.isInstanceOf[ScFunctionDeclaration] ||
                  method.hasModifierProperty("override")
      case _ => false //todo:
    }
  }
}