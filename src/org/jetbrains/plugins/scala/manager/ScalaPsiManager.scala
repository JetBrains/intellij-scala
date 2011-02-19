package org.jetbrains.plugins.scala.manager

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.{PsiManagerImpl, PsiTreeChangeEventImpl, PsiTreeChangePreprocessor}
import com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType
import com.intellij.psi.{PsiElement, PsiFile}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaPsiManager(project: Project, manager: PsiManagerImpl)
  extends AbstractProjectComponent(project) with PsiTreeChangePreprocessor {
  import PsiEventType._

  override def initComponent: Unit = {
    manager.addTreeChangePreprocessor(this)
  }

  def treeChanged(event: PsiTreeChangeEventImpl): Unit = {
    //todo: implement idea about code blocks
    event.getCode match {
      case BEFORE_CHILDREN_CHANGE =>
        if (event.getParent.isInstanceOf[PsiFile]) {
        }
      case CHILDREN_CHANGED =>
      case BEFORE_CHILD_ADDITION | CHILD_REMOVED | BEFORE_CHILD_REMOVAL | CHILD_ADDED =>
      case PROPERTY_CHANGED | BEFORE_PROPERTY_CHANGE=>
      case CHILD_REPLACED | BEFORE_CHILD_REPLACEMENT=>
      case BEFORE_CHILD_MOVEMENT =>
      case CHILD_MOVED | BEFORE_CHILD_MOVEMENT=>
      case _ => //unsupported modification code
    }
  }
}