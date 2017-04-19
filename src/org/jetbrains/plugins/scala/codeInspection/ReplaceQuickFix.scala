package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createElementFromText

/**
  * @author Pavel Fatin
  */
class ReplaceQuickFix(family: String, name: String, substitution: String) extends LocalQuickFix {
  override def getFamilyName: String = family

  override def getName: String = name

  override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    descriptor.getPsiElement.replace(createElementFromText(substitution)(project))
  }
}