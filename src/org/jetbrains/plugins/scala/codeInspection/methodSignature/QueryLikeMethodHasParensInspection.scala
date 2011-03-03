package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._

class QueryLikeMethodHasParensInspection extends LocalInspectionTool {
  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Query-like method has empty parens"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getStaticDescription =
    "Method with query-like name declared with parens"

  override def getID = "QueryLikeMethodHasParens"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasQueryLikeName && f.hasEmptyParens && !f.hasUnitReturnType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new RemoveParensQuickFix(f))
  }
}