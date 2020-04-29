package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaLanguage

object ScalaPsiChangeListener {

  def apply(onPsiChange: PsiElement => Unit,
            onPropertyChange: () => Unit): PsiTreeChangeListener = {

    new ScalaPsiChangeListenerImpl(onPsiChange, onPropertyChange, ScalaPsiEventFilter.defaultFilters)
  }

  private sealed trait EventType
  private object EventType {
    object ChildRemoved    extends EventType
    object ChildAdded      extends EventType
    object ChildMoved      extends EventType
    object ChildReplaced   extends EventType
    object ChildrenChanged extends EventType
  }
  import EventType._

  private class ScalaPsiChangeListenerImpl(onScalaPsiChange: PsiElement => Unit,
                                           onNonScalaChange: () => Unit,
                                           filters: Seq[ScalaPsiEventFilter])
    extends PsiTreeChangeAdapter {

    private def onPsiChangeEvent(event: PsiTreeChangeEvent,
                                 eventType: EventType): Unit = {
      if (filters.exists(_.shouldSkip(event)))
        return

      val newElement = extractNewElement(event, eventType)
      val oldElement = extractOldElement(event, eventType)
      val validElement = extractValidElement(event, eventType)

      if (validElement.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
        if (!filters.exists(_.shouldSkip(newElement, oldElement))) {
          onScalaPsiChange(validElement)
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

  private def extractNewElement(event: PsiTreeChangeEvent, eventType: EventType): Option[PsiElement] = eventType match {
    case EventType.ChildRemoved    => None
    case EventType.ChildAdded      => Some(event.getChild)
    case EventType.ChildMoved      => Some(event.getChild)
    case EventType.ChildReplaced   => Some(event.getNewChild)
    case EventType.ChildrenChanged => Some(event.getParent)
  }

  private def extractOldElement(event: PsiTreeChangeEvent, eventType: EventType): Option[PsiElement] = eventType match {
    case EventType.ChildRemoved    => Some(event.getChild)
    case EventType.ChildAdded      => None
    case EventType.ChildMoved      => None
    case EventType.ChildReplaced   => Some(event.getOldChild)
    case EventType.ChildrenChanged => None
  }

  private def extractValidElement(event: PsiTreeChangeEvent, eventType: EventType): PsiElement =
    extractNewElement(event, eventType).getOrElse(event.getParent)
}
