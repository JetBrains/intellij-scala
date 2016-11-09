package org.jetbrains.plugins.scala.lang.macros.expansion

import java.awt.event.MouseEvent
import java.util
import javax.swing.Icon

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon._
import com.intellij.icons.AllIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.util.Function
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.JavaConversions._
import scala.meta.intellij.ExpansionUtil

class MacroExpansionLineMarkerProvider extends RelatedItemLineMarkerProvider {

  private type Marker = RelatedItemLineMarkerInfo[_ <: PsiElement]
  private type Markers = util.Collection[_ >: Marker]

  override def collectNavigationMarkers(element: PsiElement, result: Markers): Unit = {
    if (element.getNode == null || element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER ) return
    processElement(element).foreach(result.add)
  }

  private def processElement(element: PsiElement): Option[Marker] = {
    element.getParent match {
      case holder: ScAnnotationsHolder =>
        val metaAnnot: Option[ScAnnotation] = holder.annotations.find(_.isMetaAnnotation)
        metaAnnot.map { annot =>
          ExpansionUtil.getCompiledMetaAnnotClass(annot) match {
            case Some(clazz) if ExpansionUtil.isUpToDate(annot, clazz) => createExpandLineMarker(annot.getFirstChild, annot)
            case _                                                     => createNotCompiledLineMarker(annot.getFirstChild, annot)
          }
        }
      case _ => None
    }
  }

  private def createExpandLineMarker(element: PsiElement, annot: ScAnnotation): Marker = {
    newMarker(element, AllIcons.General.ExpandAllHover, ScalaBundle.message("scala.meta.expand")) { _=>
      MacroExpandAction.expandMetaAnnotation(annot)
    }
  }

  private def createNotCompiledLineMarker(element: PsiElement, annot: ScAnnotation): Marker = {
    import org.jetbrains.plugins.scala.project._
    newMarker(element, AllIcons.General.Help, ScalaBundle.message("scala.meta.recompile")) { elt =>
      CompilerManager.getInstance(elt.getProject).make(annot.constructor.reference.get.resolve().module.get,
        new CompileStatusNotification {
          override def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) = {
            DaemonCodeAnalyzer.getInstance(elt.getProject).restart(elt.getContainingFile)
          }
        }
      )
    }
  }

  private def collectReflectExpansions(elements: util.List[PsiElement], result: Markers): Unit = {
    val expansions = elements.map(p => (p, p.getCopyableUserData(MacroExpandAction.EXPANDED_KEY))).filter(_._2 != null)
    val res = expansions.map { case (current, saved) =>
      newMarker(current, AllIcons.General.ExpandAllHover, "Undo Macro Expansion") { _ =>
        inWriteAction {
          val newPsi = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(saved)(PsiManager.getInstance(current.getProject))
          current.replace(newPsi)
          saved
        }
      }
    }
    result.addAll(res)
  }

  private def newMarker[T](elem: PsiElement, icon: Icon, caption: String)(fun: PsiElement => T): Marker = {
    new RelatedItemLineMarkerInfo[PsiElement](elem, new TextRange(elem.getTextRange.getStartOffset, elem.getTextRange.getStartOffset), icon, Pass.LINE_MARKERS,
      new Function[PsiElement, String] {
        def fun(param: PsiElement): String = caption
      },
      new GutterIconNavigationHandler[PsiElement] {
        def navigate(mouseEvent: MouseEvent, elt: PsiElement) = fun(elt)
      },
      GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]())
  }
}
