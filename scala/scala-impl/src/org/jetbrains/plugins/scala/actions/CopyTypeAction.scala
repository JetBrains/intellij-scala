package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec

final class CopyTypeAction extends AnAction(ScalaBundle.message("copy.scala.type")) {
  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setVisible(isInScalaFile(e))

    val typeableElement = getSelectedTypeableElement(e)
    e.getPresentation.setEnabled(typeableElement.isDefined)
  }

  override def actionPerformed(event: AnActionEvent): Unit = {
    val typeableElement = getSelectedTypeableElement(event)
    for {
      element <- typeableElement
      typeText <- getElementTypePresentation(element)
    } {
      updateCopyBuffer(typeText)
    }
  }

  @RequiresEdt
  private def updateCopyBuffer(typeText: String): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      CopyTypeAction.copyToClipboardListeners.values().forEach(_(typeText))
    } else {
      val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
      clipboard.setContents(new StringSelection(typeText), null)
    }
  }

  private def isInScalaFile(e: AnActionEvent): Boolean = {
    val context = e.getDataContext
    val project: Project = CommonDataKeys.PROJECT.getData(context)
    val editor: Editor = CommonDataKeys.EDITOR.getData(context)

    if (project == null || editor == null) false
    else {
      val file = PsiUtilBase.getPsiFileInEditor(editor, project)
      file.is[ScalaFile]
    }
  }

  @RequiresBackgroundThread // can involve heavy resolution in complex code bases
  private def getElementTypePresentation(element: ScalaPsiElement with Typeable): Option[String] = {
    val typeResult = element match {
      case expr: ScExpression =>
        expr.getTypeWithoutImplicits(ignoreBaseType = true)
      case _ =>
        element.`type`()
    }
    typeResult.toOption.map { typ =>
      val typeProcessed = typ.removeAliasDefinitions().tryExtractDesignatorSingleton

      implicit val tpc: TypePresentationContext = TypePresentationContext(element)
      implicit val context: Context = Context(element)

      typeProcessed.presentableText
    }
  }

  private def getSelectedTypeableElement(e: AnActionEvent): Option[ScalaPsiElement with Typeable] = {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)

    if (project == null || editor == null)
      return None

    val file = PsiUtilBase.getPsiFileInEditor(editor, project) match {
      case file: ScalaFile => file
      case _ =>
        return None
    }
    val selectionModel = editor.getSelectionModel
    val startOffset = selectionModel.getSelectionStart
    val endOffset = selectionModel.getSelectionEnd

    getSelectedTypeableElement(startOffset, endOffset, file)
  }

  private def getSelectedTypeableElement(start: Int, end: Int, file: ScalaFile): Option[ScalaPsiElement with Typeable] = {
    @tailrec
    @Nullable
    def skipWsAndCommend(@Nullable e: PsiElement, next: PsiElement => PsiElement): PsiElement = e match {
      case _: PsiWhiteSpace | _: PsiComment => skipWsAndCommend(next(e), next)
      case _ => e
    }

    val fixedStartIndex = if (start == file.getTextLength && start > 0) start - 1 else start
    val startElement = skipWsAndCommend(file.findElementAt(fixedStartIndex), _.getNextSibling)
    val fixedEndIndex = if (end > fixedStartIndex) end - 1 else end
    val endElement = skipWsAndCommend(file.findElementAt(fixedEndIndex), _.getPrevSibling)

    if (startElement == null || endElement == null) {
      return None
    }

    @tailrec
    def findTypeableParentElement(@Nullable e: PsiElement): Option[ScalaPsiElement with Typeable] = {
      if (e == null) {
        return None
      }

      if (e.startOffset < startElement.startOffset && e.endOffset > endElement.endOffset) {
        return None
      }

      if (e.endOffset >= endElement.startOffset) {
        e match {
          case e: ScalaPsiElement with Typeable  =>
            return Some(e)
          case Parent(p: ScTypeDefinition) if p.nameId == e =>
            return Some(p)
          case Parent(f: ScFunction) if f.nameId == e =>
            return Some(f)
          case _ =>
        }
      }

      findTypeableParentElement(e.getParent)
    }


    findTypeableParentElement(startElement)
  }
}

object CopyTypeAction {
  val ActionId: String = "Scala.CopyType"

  private val copyToClipboardListeners: ConcurrentHashMap[Any, String => Unit] = new ConcurrentHashMap
  private[actions] def withUnitTestClipboardListener[T](listener: String => Unit)(body: => T): T = {
    val token = new Object
    copyToClipboardListeners.put(token, listener)
    try body
    finally copyToClipboardListeners.remove(token)
  }
}
