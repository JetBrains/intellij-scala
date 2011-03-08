package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.VisitorWrapper

/**
 * Pavel Fatin
 */

abstract class AbstractInspection(id: String, name: String) extends LocalInspectionTool {
  override final def getID = id

  final def getGroupDisplayName = InspectionsUtil.MethodSignature

  final def getDisplayName = name

  final def getShortName = name

  override final def isEnabledByDefault = true

  override final def getStaticDescription = description

  @Language("HTML")
  val description: String

  override final def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper(actionFor(holder))

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any]
}