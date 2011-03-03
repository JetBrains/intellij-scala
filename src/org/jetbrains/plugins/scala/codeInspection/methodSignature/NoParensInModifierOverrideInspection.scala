package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

class NoParensInModifierOverrideInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "No parens in Java modifier method override"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "No parens in Java modifier method override"

  override def getID = "NoParensInModifierOverride"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if !f.hasEmptyParens && !f.hasUnitReturnType =>
      f.superMethod match {
        case Some(_: ScalaPsiElement) => // do nothing
        case Some(method) if method.isModifier =>
          holder.registerProblem(f.nameId, getDisplayName, new AddParensQuickFix(f))
        case _ =>
      }
  }
}