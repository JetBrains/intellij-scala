package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.AddEmptyParentheses
import org.intellij.lang.annotations.Language

class EmptyParenMethodOverridenAsParameterless extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<p>The convention is that you include parentheses if the method has <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p>In accordance with <a href="http://en.wikipedia.org/wiki/Liskov_substitution_principle">Liskov substitution principle</a>,
as overriden method is empty-paren, the overriding method must also be declared as a method with side effects.</p>
<p><small>* Refer to Programming in Scala, 5.3 Operators are methods</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Empy-paren Scala method overriden as parameterless"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "EmptyParenMethodOverridenAsParameterless"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.isParameterless && !f.hasUnitReturnType =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if method.isEmptyParen =>
          holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
        case _ =>
      }
  }
}