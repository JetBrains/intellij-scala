package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._

class AccessorLikeMethodIsUnit extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription =
"""Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming contract for accessors are expected
to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

However, methods with a result type of <code>Unit</code> are only executed for their side effects.

<small>* Refer to Programming in Scala, 2.3 Define some functions</small>"""

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Method with accessor-like name has Unit result type"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "AccessorLikeMethodIsUnit"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasQueryLikeName && f.hasUnitReturnType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName)
  }
}