package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiTreeChangeAdapter, PsiTreeChangeEvent, PsiTreeChangeListener}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

object ScalaPsiChangeListener {
  def apply(onPsiChange: PsiElement => Unit,
            onPropertyChange: () => Unit): PsiTreeChangeListener =
    new DefaultScalaPsiChangeListener(onPsiChange, onPropertyChange)


  private sealed trait EventType
  private object EventType {
    object ChildRemoved    extends EventType
    object ChildAdded      extends EventType
    object ChildMoved      extends EventType
    object ChildReplaced   extends EventType
    object ChildrenChanged extends EventType
  }
  import EventType._

  private class DefaultScalaPsiChangeListener(onScalaPsiChange: PsiElement => Unit, onNonScalaChange: () => Unit)
    extends PsiTreeChangeAdapter {

    protected def shouldSkip(event: PsiTreeChangeEvent): Boolean = {
      isGenericChange(event) || isFromIdeaInternalFile(event)
    }

    protected def shouldSkip(element: PsiElement): Boolean = {
      // do not update on changes in dummy file or comments
      PsiTreeUtil.getParentOfType(element, classOf[ScalaCodeFragment], classOf[PsiComment]) != null
    }

    private def onPsiChangeEvent(event: PsiTreeChangeEvent,
                                 eventType: EventType): Unit = {
      if (shouldSkip(event))
        return

      val element = extractChangedPsi(event, eventType)

      if (element.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
        if (!shouldSkip(element)) {
          onScalaPsiChange(element)
        }
      }
      else onNonScalaChange()
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildRemoved)

    override def childReplaced(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildReplaced)

    override def childAdded(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildAdded)

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildrenChanged)

    override def childMoved(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildMoved)

    override def propertyChanged(event: PsiTreeChangeEvent): Unit = onNonScalaChange()
  }

  private def isFromIdeaInternalFile(event: PsiTreeChangeEvent) = {
    val vFile = event.getFile match {
      case null => event.getOldValue.asOptionOf[VirtualFile]
      case file =>
        val fileType = file.getFileType
        if (fileType == ScalaFileType.INSTANCE || fileType == JavaFileType.INSTANCE) None
        else Option(file.getVirtualFile)
    }
    vFile.exists(ProjectUtil.isProjectOrWorkspaceFile)
  }

  private def isGenericChange(event: PsiTreeChangeEvent) = event match {
    case impl: PsiTreeChangeEventImpl => impl.isGenericChange
    case _ => false
  }

  private def extractChangedPsi(event: PsiTreeChangeEvent, eventType: EventType): PsiElement = eventType match {
    case EventType.ChildRemoved    => event.getParent
    case EventType.ChildAdded      => event.getChild
    case EventType.ChildMoved      => event.getChild
    case EventType.ChildReplaced   => event.getNewChild
    case EventType.ChildrenChanged => event.getParent
  }
}
