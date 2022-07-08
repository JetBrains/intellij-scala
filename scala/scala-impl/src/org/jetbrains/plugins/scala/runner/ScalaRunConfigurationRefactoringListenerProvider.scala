package org.jetbrains.plugins.scala
package runner

import com.intellij.execution.impl.RunConfigurationRefactoringElementListenerProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

class ScalaRunConfigurationRefactoringListenerProvider extends RunConfigurationRefactoringElementListenerProvider {
  private def wrap(td: ScTemplateDefinition) = new PsiClassWrapper(td, td.qualifiedName, td.name)
  private def decorate(listener: RefactoringElementListener): RefactoringElementListener = {
    if (listener == null) return null

    new RefactoringElementListener {
      override def elementMoved(newElement: PsiElement): Unit = newElement match {
        case td: ScTemplateDefinition => listener.elementMoved(wrap(td))
        case _ =>
      }
      override def elementRenamed(newElement: PsiElement): Unit = newElement match {
        case td: ScTemplateDefinition => listener.elementRenamed(wrap(td))
        case _ =>
      }
    }
  }

  override def getListener(element: PsiElement): RefactoringElementListener = {
    element match {
      case td: ScTemplateDefinition =>
        val wrapper = wrap(td)
        decorate(super.getListener(wrapper))
      case _ => null
    }
  }
}
