package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.VisitorWrapper

/**
 * Pavel Fatin
 */
abstract class  AbstractInspection(id: String, name: String) extends LocalInspectionTool {
  def this(name: String) {
    this(getClass.getSimpleName.stripSuffix("Inspection"), name)
  }

  override def getDisplayName: String = name

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper(actionFor(holder))

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any]
}