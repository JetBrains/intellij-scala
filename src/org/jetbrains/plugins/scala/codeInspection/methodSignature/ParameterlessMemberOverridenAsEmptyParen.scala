package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.RemoveParentheses

class ParameterlessMemberOverridenAsEmptyParen extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<p>The recommended convention is to use a parameterless method whenever there are no parameters
and the method have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p>This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>,
which says that client code should not be affected by a decision to implement an attribute as a field or method.</p>
<p>In accordance with <a href="http://en.wikipedia.org/wiki/Liskov_substitution_principle">Liskov substitution principle</a>,
as overriden method is parameterless, the overriding method must also be declared as a method without side effects.</p>
<p><small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Parameterless Scala member overriden as empty-paren"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "ParameterlessMemberOverridenAsEmptyParen"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.isEmptyParen && !f.hasUnitReturnType =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(method: ScFunction) if !method.isEmptyParen =>
          holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
        case _ =>
      }
  }
}