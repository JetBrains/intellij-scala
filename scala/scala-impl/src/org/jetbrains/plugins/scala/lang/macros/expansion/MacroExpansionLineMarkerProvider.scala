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
import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpandAction.UndoExpansionData
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScalaMetaMode

abstract class MacroExpansionLineMarkerProvider extends RelatedItemLineMarkerProvider {

  type Marker = RelatedItemLineMarkerInfo[_ <: PsiElement]
  type Markers = util.Collection[_ >: Marker]

  override def collectNavigationMarkers(element: PsiElement, result: Markers): Unit = {
    if (ScalaProjectSettings.getInstance(element.getProject).getScalaMetaMode == ScalaMetaMode.Disabled) return
    if (element.getNode == null || element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER ) return
    getExpandMarker(element).foreach(result.add)
    getUndoMarker(element).foreach(result.add)
  }

  protected def getExpandMarker(element: PsiElement): Option[Marker]

  protected def getUndoMarker(element: PsiElement): Option[Marker]

  protected def createNotCompiledLineMarker(element: PsiElement, annot: ScAnnotation): Marker = {
    import org.jetbrains.plugins.scala.project._
    val eltPointer = element.createSmartPointer
    newMarker(element, AllIcons.General.Help, ScalaBundle.message("scala.meta.recompile")) { elt =>
      CompilerManager.getInstance(elt.getProject).make(annot.constructor.reference.get.resolve().module.get,
        new CompileStatusNotification {
          override def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
            if (!compileContext.getProject.isDisposed)
              DaemonCodeAnalyzer.getInstance(eltPointer.getElement.getProject).restart(eltPointer.getElement.getContainingFile)
          }
        }
      )
    }
  }

  protected def newMarker[T <: PsiElement, R](elem: T, icon: Icon, caption: String)(fun: T => R): Marker = {
    new RelatedItemLineMarkerInfo[PsiElement](elem, new TextRange(elem.getTextRange.getStartOffset, elem.getTextRange.getStartOffset), icon, Pass.LINE_MARKERS,
      new Function[PsiElement, String] {
        def fun(param: PsiElement): String = caption
      },
      new GutterIconNavigationHandler[PsiElement] {
        def navigate(mouseEvent: MouseEvent, elt: PsiElement): Unit = fun(elt.asInstanceOf[T])
      },
      GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]())
  }

  protected def newExpandMarker[T <: PsiElement, R](elem: T)(fun: T => R): Marker = {
    newMarker(elem, AllIcons.General.ExpandAllHover, ScalaBundle.message("scala.meta.expand"))(fun)
  }

  protected def newUndoMarker[T](element: PsiElement): Marker = {
    val parent = element.getParent

    def undoExpansion(original: String, companion: Option[String] = None): Unit = {
      val newPsi = ScalaPsiElementFactory
        .createBlockExpressionWithoutBracesFromText(original.trim)(PsiManager.getInstance(element.getProject))
      (parent, companion) match {
        case (td: ScTypeDefinition, Some(companionText)) =>
          val definition = ScalaPsiElementFactory.createTypeDefinitionWithContext(companionText.trim, parent.getContext, null)
          td.baseCompanionModule.foreach(_.replace(definition))
        case (td: ScTypeDefinition, None) =>
          td.baseCompanionModule.foreach(c => c.getParent.getNode.removeChild(c.getNode))
        case _ => None
      }
      parent.replace(newPsi)

    }
    val UndoExpansionData(original, savedCompanion) = parent.getCopyableUserData(MacroExpandAction.EXPANDED_KEY)
    newMarker(element, AllIcons.Actions.Undo, "Undo macro expansion") { _ =>
      inWriteCommandAction(element.getProject)(undoExpansion(original, savedCompanion))
    }
  }
}
