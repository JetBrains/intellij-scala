package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import quickfix.RemoveTypeAnnotation

/**
 * Pavel Fatin
 */

class UnitMethodDeclaredWithTypeAnnotation extends AbstractMethodSignatureInspection(
  "ScalaUnitMethodDeclaredWithTypeAnnotation", "Redundant Unit result type annotation") {

  @Language("HTML")
  val description =
 """<code>Unit</code> result type annotation is redundant:

<pre><code><span style="color:#808080">  // excessive clutter</span><br>  <strong style="color:#000080">def</strong> foo(): Unit
  <span style="color:#808080">// concise form</span><br>  <strong style="color:#000080">def</strong> foo()</code></pre>
<small>* Refer to Programming in Scala, 2.3 Define some functions</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDeclaration if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new RemoveTypeAnnotation(f))
      }
  }
}