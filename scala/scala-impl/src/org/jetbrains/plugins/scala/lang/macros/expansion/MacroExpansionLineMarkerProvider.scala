package org.jetbrains.plugins.scala.lang.macros.expansion

import java.awt.event.MouseEvent
import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon._
import com.intellij.icons.AllIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiManager, PsiWhiteSpace}
import com.intellij.util.Function
import javax.swing.Icon
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScalaMetaMode
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups.toolWindowGroup

import scala.collection.mutable.ArrayBuffer

abstract class MacroExpansionLineMarkerProvider extends RelatedItemLineMarkerProvider {

  type Marker = RelatedItemLineMarkerInfo[_]
  type Markers = util.Collection[_ >: Marker]

  case class UndoExpansionData(original: String, companion: Option[String] = None)

  protected val EXPANDED_KEY = new Key[UndoExpansionData]("MACRO_EXPANDED_KEY")

  protected lazy val messageGroup: NotificationGroup = toolWindowGroup

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
    val module = annot.constructorInvocation.reference.get.resolve().module
    if (module.isEmpty)
      Logger.getInstance(getClass).error(s"No bound module for annotation ${annot.getText}")

    createMarker(element, AllIcons.General.ContextHelp, ScalaBundle.message("scala.meta.recompile")) { elt =>
      CompilerManager.getInstance(elt.getProject).make(module.get,
        new CompileStatusNotification {
          override def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
            if (!compileContext.getProject.isDisposed)
              DaemonCodeAnalyzer.getInstance(eltPointer.getElement.getProject).restart(eltPointer.getElement.getContainingFile)
          }
        }
      )
    }
  }

  protected def createMarker[T <: PsiElement, R](elem: T, icon: Icon, caption: String)(fun: T => R): Marker = {
    new RelatedItemLineMarkerInfo[PsiElement](elem, new TextRange(elem.getTextRange.getStartOffset, elem.getTextRange.getStartOffset), icon,
      new Function[PsiElement, String] {
        override def fun(param: PsiElement): String = caption
      },
      new GutterIconNavigationHandler[PsiElement] {
        override def navigate(mouseEvent: MouseEvent, elt: PsiElement): Unit = fun(elt.asInstanceOf[T])
      },
      GutterIconRenderer.Alignment.RIGHT,
      () => util.Arrays.asList[GotoRelatedItem]()
    )
  }

  protected def createExpandMarker[T <: PsiElement, R](elem: T)(fun: T => R): Marker = {
    createMarker(elem, AllIcons.Actions.Expandall, ScalaBundle.message("scala.meta.expand"))(fun)
  }

  protected def createUndoMarker[T](element: PsiElement): Marker = {
    val parent = element.getParent

    def undoExpansion(original: String, companion: Option[String] = None): Unit = {
      val newPsi = ScalaPsiElementFactory
        .createBlockExpressionWithoutBracesFromText(original.trim)(PsiManager.getInstance(element.getProject))
      (parent, companion) match {
        case (td: ScTypeDefinition, Some(companionText)) =>
          val definition = ScalaPsiElementFactory.createTypeDefinitionWithContext(companionText.trim, parent.getContext, null)
          td.baseCompanion.foreach(_.replace(definition))
        case (td: ScTypeDefinition, None) =>
          td.baseCompanion.foreach(c => c.getParent.getNode.removeChild(c.getNode))
        case _ => None
      }
      parent.replace(newPsi)

    }
    val UndoExpansionData(original, savedCompanion) = parent.getCopyableUserData(EXPANDED_KEY)
    createMarker(element, AllIcons.Actions.Undo, ScalaBundle.message("undo.macro.expansion")) { _ =>
      inWriteCommandAction(undoExpansion(original, savedCompanion))(element.getProject)
    }
  }

  protected def reformatCode(psi: PsiElement): PsiElement = {
    val res = CodeStyleManager.getInstance(psi.getProject).reformat(psi)
    val tobeDeleted = new ArrayBuffer[PsiElement]
    val v = new PsiElementVisitor {
      override def visitElement(element: PsiElement): Unit = {
        if (element.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val nextLeaf = file.findElementAt(element.getTextRange.getEndOffset)
          if (nextLeaf.isInstanceOf[PsiWhiteSpace] && nextLeaf.getText.contains("\n")) {
            tobeDeleted += element
          }
        }
        element.acceptChildren(this)
      }
    }
    v.visitElement(res)
    tobeDeleted.foreach(_.delete())
    res
  }

}
