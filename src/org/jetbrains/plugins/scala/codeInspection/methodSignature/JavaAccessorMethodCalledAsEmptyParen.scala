package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.VisitorWrapper
import org.jetbrains.plugins.scala.Extensions._
import quickfix.RemoveCallParentheses

class JavaAccessorMethodCalledAsEmptyParen extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<p>Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming contract for accessors
are expected to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.</p>
<p>The recommended convention is to use a parameterless method whenever there are no parameters
and the method have no side effect.</p>
<p>This convention supports the <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">uniform access principle</a>,
which says that client code should not be affected by a decision to implement an attribute as a field or method.</p>
<p>The problem is that Java does not implement the uniform access principle.</p>
<p>To bridge that gap, Scala allows you to leave off the empty parentheses on an invocation of any function that takes no arguments.</p>
<p><small>* Refer to Programming in Scala, 10.3 Defining parameterless methods</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Java accessor method called as empty-paren"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "JavaAccessorMethodCalledAsEmptyParen"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case e: ScReferenceExpression => e.getParent match {
      case call: ScMethodCall if call.argumentExpressions.isEmpty => e.resolve match {
        case _: ScalaPsiElement => // do nothing
        case (m: PsiMethod) if m.isAccessor =>
          holder.registerProblem(e.nameId, getDisplayName, new RemoveCallParentheses(call))
        case _ =>
      }
      case _ =>
    }
  }
}
