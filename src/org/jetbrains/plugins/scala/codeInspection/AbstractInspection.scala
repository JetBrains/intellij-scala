package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.VisitorWrapper

/**
 * Pavel Fatin
 */
abstract class  AbstractInspection(id: String, name: String) extends LocalInspectionTool {
  override def getDisplayName: String = name

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper(actionFor(holder))

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any]
}