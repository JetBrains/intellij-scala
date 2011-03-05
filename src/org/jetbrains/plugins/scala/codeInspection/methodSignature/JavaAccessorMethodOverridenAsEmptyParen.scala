package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.Extensions._
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.RemoveParentheses

class JavaAccessorMethodOverridenAsEmptyParen extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription =
"""Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming contract for accessors are expected
to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

The recommended convention is to use a parameterless method whenever there are
no parameters and the method have no side effect.

This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>, which says that client code
should not be affected by a decision to implement an attribute as a field or method.

The problem is that Java does not implement the uniform access principle.

To bridge that gap, Scala allows you to override an empty-paren method with
a parameterless method.

In accordance with <a href="http://en.wikipedia.org/wiki/Liskov_substitution_principle">Liskov substitution principle</a>, as overriden method has no side effects,
the overriding method must also be declared as a method without side effects.

<small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small>"""

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Java accessor method overriden as empty-paren"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "JavaAccessorMethodOverridenAsEmptyParen"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunction if f.isEmptyParen && !f.hasUnitReturnType =>
      f.superMethods.headOption match {  // f.superMethod returns None for some reason
        case Some(_: ScalaPsiElement) => // do nothing
        case Some(method) if method.isAccessor =>
          holder.registerProblem(f.nameId, getDisplayName, new RemoveParentheses(f))
        case _ =>
      }
  }
}