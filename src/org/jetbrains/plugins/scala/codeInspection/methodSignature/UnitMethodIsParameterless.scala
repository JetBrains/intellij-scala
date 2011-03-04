package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.AddEmptyParentheses

class UnitMethodIsParameterless extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<p>Methods with a result type of <code>Unit</code> are only executed for their <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p>The convention is that you include parentheses if the method has side effects.</p>
<p><small>* Refer to Programming in Scala, 5.3 Operators are methods</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Method with Unit result type is parameterless"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "UnitMethodIsParameterless"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.isParameterless && f.hasUnitReturnType =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}

// TODO descriptions
// TODO tests
// TODO warning: Detected apparent refinement of Unit; are you missing an '=' sign?
// TODO call to Scala method with parens or Java modifier and forgot parens
// TODO call to Java property and use parens