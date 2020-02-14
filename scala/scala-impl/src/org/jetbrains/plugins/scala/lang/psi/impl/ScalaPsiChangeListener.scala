package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.{PsiElement, PsiTreeChangeAdapter, PsiTreeChangeEvent, PsiTreeChangeListener}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt}

object ScalaPsiChangeListener {
  def apply(onPsiChange: PsiElement => Unit,
            onPropertyChange: () => Unit): PsiTreeChangeListener =
    new DefaultScalaPsiChangeListener(onPsiChange, onPropertyChange)

  private class DefaultScalaPsiChangeListener(onPsiChange: PsiElement => Unit, onPropertyChange: () => Unit)
    extends PsiTreeChangeAdapter {

    private def fromIdeaInternalFile(event: PsiTreeChangeEvent) = {
      val vFile = event.getFile match {
        case null => event.getOldValue.asOptionOf[VirtualFile]
        case file =>
          val fileType = file.getFileType
          if (fileType == ScalaFileType.INSTANCE || fileType == JavaFileType.INSTANCE) None
          else Option(file.getVirtualFile)
      }
      vFile.exists(ProjectUtil.isProjectOrWorkspaceFile)
    }

    private def shouldSkip(event: PsiTreeChangeEvent): Boolean = {
      event match {
        case impl: PsiTreeChangeEventImpl if impl.isGenericChange => false
        case _ if fromIdeaInternalFile(event)                     => false
        case _                                                    => true
      }
    }

    private def onPsiChangeEvent(event: PsiTreeChangeEvent,
                                 psiElement: PsiElement): Unit = {
      if (psiElement == null || shouldSkip(event))
        return

      onPsiChange(psiElement)
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, event.getParent)

    override def childReplaced(event: PsiTreeChangeEvent): Unit = {
      val parent = event.getParent

      // Ignore changed literals as long as the type is preserved
      if (!parent.module.exists(_.literalTypesEnabled) && parent.is[ScLiteral] && !parent.is[ScInterpolatedStringLiteral]) {
        return
      }

      val changedElement =
        if (event.getNewChild.getClass == event.getOldChild.getClass)
          event.getNewChild
        else parent

      onPsiChangeEvent(event, changedElement)
    }

    override def childAdded(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, event.getChild)

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, event.getParent)

    override def childMoved(event: PsiTreeChangeEvent): Unit = onPsiChangeEvent(event, event.getChild)

    override def propertyChanged(event: PsiTreeChangeEvent): Unit = onPropertyChange()
  }
}
