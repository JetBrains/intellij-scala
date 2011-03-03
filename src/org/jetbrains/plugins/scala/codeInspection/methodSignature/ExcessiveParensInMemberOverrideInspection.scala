package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScValue, ScFunction}

class ExcessiveParensInMemberOverrideInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Excessive parens in member override"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method adds parens that is absent in overriden member"

  override def getID = "ExcessiveParensInMemberOverride"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasEmptyParens && !f.hasUnitReturnType =>
      f.superMethod match {
        case Some(method: ScFunction) if !method.hasEmptyParens =>
          holder.registerProblem(f.nameId, getDisplayName, new RemoveParensQuickFix(f))
        case _ =>
      }
  }
}