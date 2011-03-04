package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.VisitorWrapper
import quickfix.RemoveTypeAnnotation


class UnitMethodDeclaredWithTypeAnnotation extends LocalInspectionTool {
  @Language("HTML")
  override val getStaticDescription = """<html><body>
<code>Unit</code> result type annotation is redundant:
<br>
<pre><code>
  <span style="color:#808080">// excessive clutter</span>
  <strong style="color:#000080">def</strong> foo(): Unit

  <span style="color:#808080">// concise form</span>
  <strong style="color:#000080">def</strong> foo()
</code></pre>
<p><small>* Refer to Programming in Scala, 2.3 Define some functions</small></p>
</body></html>
    """

  def getGroupDisplayName = InspectionsUtil.MethodSignature

  def getDisplayName = "Redundant Unit result type annotation"

  def getShortName = getDisplayName

  override def isEnabledByDefault = true

  override def getID = "UnitMethodDeclaredWithTypeAnnotation"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = VisitorWrapper {
    case f: ScFunctionDeclaration if f.hasUnitReturnType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new RemoveTypeAnnotation(f))
      }
  }
}