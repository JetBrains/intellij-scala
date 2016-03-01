package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * @author Pavel Fatin
  */
class ReplaceQuickFix(family: String, name: String, substitution: String) extends LocalQuickFix {
  override def getFamilyName = family

  override def getName = name

  override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    val element = descriptor.getPsiElement
    val newElement = ScalaPsiElementFactory.parseElement(substitution, PsiManager.getInstance(project))
    element.replace(newElement)
  }
}