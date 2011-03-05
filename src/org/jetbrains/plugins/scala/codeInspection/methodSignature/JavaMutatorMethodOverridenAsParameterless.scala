package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import codeInspection.InspectionsUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.Extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import quickfix.AddEmptyParentheses

class JavaMutatorMethodOverridenAsParameterless extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription =
"""Methods that has mutators-like name are expected to have <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

The convention is that you include parentheses if the method has side effects.

In accordance with <a href="http://en.wikipedia.org/wiki/Liskov_substitution_principle">Liskov substitution principle</a>, as overriden method has side effects,
the overriding method must also be declared as a method with side effects.

<small>* Refer to Programming in Scala, 5.3 Operators are methods</small>"""

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Java mutator method overriden as parameterless"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "JavaMutatorMethodOverridenAsParameterless"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.isParameterless && !f.hasUnitReturnType =>
      f.superMethods.headOption match { // f.superMethod returns None for some reason
        case Some(_: ScalaPsiElement) => // do nothing
        case Some(method) if method.isMutator =>
          holder.registerProblem(f.nameId, getDisplayName, new AddEmptyParentheses(f))
        case _ =>
      }
  }
}