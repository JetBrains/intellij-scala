package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.VisitorWrapper

/**
 * Pavel Fatin
 */
abstract class AbstractInspection(id: String, name: String) extends LocalInspectionTool {
  override final def getID = id

  def getGroupDisplayName = InspectionsUtil.SCALA

  final def getDisplayName = name

  final def getShortName = name

  override def isEnabledByDefault = true

  override def getStaticDescription = description

  @Language("HTML")
  val description: String

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper(actionFor(holder))

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any]
}