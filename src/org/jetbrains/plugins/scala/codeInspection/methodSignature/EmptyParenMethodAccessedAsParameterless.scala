package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import quickfix.AddCallParentheses

class EmptyParenMethodAccessedAsParameterless extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription =
"""The convention is that method includes parentheses if it has <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

While it's possible to leave out empty parentheses in method calls (to adapt
the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a> to Java), it's recommended to still write the empty parentheses
when the invoked method represents more than a property of its receiver object.

<small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small>"""

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Empty-paren method accessed as parameterless"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "EmptyParenMethodAccessedAsParameterless"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case e: ScReferenceExpression if !e.getParent.isInstanceOf[ScMethodCall] => e.resolve match {
      case (f: ScFunction) if f.isEmptyParen =>
        holder.registerProblem(e.nameId, getDisplayName, new AddCallParentheses(e))
      case _ =>
    }
  }
}