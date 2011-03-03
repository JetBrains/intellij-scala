package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class NoParensInMethodOverrideInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "No parens in method override"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method left parens that is present in overriden method"

  override def getID = "NoParensInMethodOverride"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if !f.hasParens && !f.hasUnitReturnType =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if method.hasEmptyParens =>
          holder.registerProblem(f.nameId, getDisplayName, new AddParensQuickFix(f))
        case _ =>
      }
  }
}