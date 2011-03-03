package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil

class UnitMethodWithoutParensInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "No parens in method with Unit return type"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method with side-effect declared as property)"

  override def getID = "UnitMethodWithoutParens"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if !f.hasEmptyParens && f.hasUnitReturnType =>
      holder.registerProblem(f.nameId, getDisplayName, new AddParensQuickFix(f))
  }
}

// TODO descriptions
// TODO tests
// TODO warning: Detected apparent refinement of Unit; are you missing an '=' sign?
// TODO call to Scala method with parens or Java modifier and forgot parens
// TODO call to Java property and use parens