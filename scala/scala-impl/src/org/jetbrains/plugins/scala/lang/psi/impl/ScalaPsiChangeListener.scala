package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiChangeListener._

final class ScalaPsiChangeListener(project: Project) extends PsiTreeChangeAdapter {
  import ScalaPsiChangeListener.EventType._

  private val filters = ScalaPsiEventFilter.defaultFilters

  private lazy val psiManager = ScalaPsiManager.instance(project)

  private def onPsiChangeEvent(event: PsiTreeChangeEvent,
                               eventType: EventType): Unit = {
    if (filters.exists(_.shouldSkip(event)))
      return

    val newElement = extractNewElement(event, eventType)
    val oldElement = extractOldElement(event, eventType)
    val validElement = extractValidElement(event, eventType)

    if (validElement.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
      if (!filters.exists(_.shouldSkip(newElement, oldElement))) {
        psiManager.clearOnScalaElementChange(validElement)
      }
    }
    else psiManager.clearOnNonScalaChange()
  }

  override def childRemoved(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildRemoved)
  override def childReplaced(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildReplaced)
  override def childAdded(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildAdded)
  override def childrenChanged(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildrenChanged)
  override def childMoved(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, ChildMoved)
  override def propertyChanged(event: PsiTreeChangeEvent): Unit = psiManager.clearOnNonScalaChange()
}

object ScalaPsiChangeListener {
  private sealed trait EventType
  private object EventType {
    object ChildRemoved    extends EventType
    object ChildAdded      extends EventType
    object ChildMoved      extends EventType
    object ChildReplaced   extends EventType
    object ChildrenChanged extends EventType
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
