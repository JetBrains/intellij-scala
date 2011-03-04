package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.RemoveParentheses

class AccessorLikeMethodIsEmptyParen extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<p>Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming convention for accessors
are expected to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p>The recommended convention is to use a parameterless method whenever there are no parameters
and the method have no side effect.</p>
<p>This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>,
which says that client code should not be affected by a decision to implement an attribute as a field or method.</p>
<p><small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Method with accessor-like name is empty-paren"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "AccessorLikeMethodIsEmptyParen"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasQueryLikeName && f.isEmptyParen && !f.hasUnitReturnType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
  }
}