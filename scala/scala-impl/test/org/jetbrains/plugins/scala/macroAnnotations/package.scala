package org.jetbrains.plugins.scala

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl

/**
  * Nikolay.Tropin
  * 01-Feb-17
  */
package object macroAnnotations {
  def incModCount(project: Project): Unit = {
    val manager = PsiManager.getInstance(project)
    manager.getModificationTracker.asInstanceOf[PsiModificationTrackerImpl].incCounter()
  }
}
