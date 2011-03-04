package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.AddEmptyParentheses

class MutatorLikeMethodIsParameterless extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<p>Methods with mutator-like name are expected to have <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p>The convention is that you include parentheses if the method has <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p><small>* Refer to Programming in Scala, 5.3 Operators are methods</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Method with mutator-like name is parameterless"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "MutatorLikeMethodIsParameterless"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.hasMutatorLikeName && f.isParameterless && !f.hasUnitReturnType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
  }
}