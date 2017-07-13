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
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConversions._
import scala.meta.intellij.MetaExpansionsManager
import ScalaProjectSettings.ScalaMetaMode
import org.jetbrains.plugins.scala.lang.macros.expansion.MacroExpandAction.UndoExpansionData
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

class MacroExpansionLineMarkerProvider extends RelatedItemLineMarkerProvider {

  private type Marker = RelatedItemLineMarkerInfo[_ <: PsiElement]
  private type Markers = util.Collection[_ >: Marker]

  override def collectNavigationMarkers(element: PsiElement, result: Markers): Unit = {
    if (ScalaProjectSettings.getInstance(element.getProject).getScalaMetaMode == ScalaMetaMode.Disabled) return
    if (element.getNode == null || element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER ) return
    processElement(element).foreach(result.add)
    createUndoExpansionMarkers(element, result)
  }

  private def processElement(element: PsiElement): Option[Marker] = {
    element.getParent match {
      case holder: ScAnnotationsHolder =>
        val metaAnnot: Option[ScAnnotation] = holder.annotations.find(_.isMetaAnnotation)
        metaAnnot.map { annot =>
          MetaExpansionsManager.getCompiledMetaAnnotClass(annot) match {
            case Some(clazz) if MetaExpansionsManager.isUpToDate(annot, clazz) => createExpandLineMarker(annot.getFirstChild, annot)
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

  private def createUndoExpansionMarkers(element: PsiElement, result: Markers): Unit = {
    val parent = element.getParent

    def undoExpansion(original: String, companion: Option[String] = None): Unit = {
      val newPsi = ScalaPsiElementFactory
        .createBlockExpressionWithoutBracesFromText(original.trim)(PsiManager.getInstance(element.getProject))
      parent match {
        case td: ScTypeDefinition if companion.isDefined =>
          val definition = ScalaPsiElementFactory.createTypeDefinitionWithContext(companion.get.trim, parent.getContext, null)
          td.baseCompanionModule.foreach(_.replace(definition))
        case _ => None
      }
      parent.replace(newPsi)

    }

    def mkUndoMarker(undoFunc: => Unit) = newMarker(element, AllIcons.Actions.Undo, "Undo macro expansion") { _ =>
      inWriteAction(undoFunc)
    }

    parent.getCopyableUserData(MacroExpandAction.EXPANDED_KEY) match {
      case UndoExpansionData(original, savedCompanion) =>
        result.add(mkUndoMarker(undoExpansion(original, savedCompanion)))
      case _ =>
    }
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
