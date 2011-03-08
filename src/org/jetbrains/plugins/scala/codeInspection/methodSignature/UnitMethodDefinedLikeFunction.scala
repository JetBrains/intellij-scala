package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import quickfix.RemoveTypeAnnotationAndEqualSign

/**
 * Pavel Fatin
 */

class UnitMethodDefinedLikeFunction extends AbstractInspection(
  "UnitMethodDefinedLikeFunction", "Method with Unit result type defined like function") {

  @Language("HTML")
  val description =
"""Methods with a result type of <code>Unit</code> are only executed for their <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

A better way to express such methods is to leave off the result type and the equals sign,
and enclose the body of the method in curly braces.

In this form, the method looks like a <dfn>procedure</dfn>, a method that is executed only
for its side effects:

<pre><code>  <span style="color:#808080">// excessive clutter, looks like a function</span><br>  <strong style="color:#000080">def</strong> close(): Unit = { file.delete() }
  <span style="color:#808080">// concise form, side-effect is clearly stated</span><br>  <strong style="color:#000080">def</strong> close() { file.delete() }</code></pre>
<small>* Refer to Programming in Scala, 4.1 Classes, fields, and methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDefinition if f.hasUnitResultType =>
      f.returnTypeElement.foreach { e =>
        holder.registerProblem(e, getDisplayName, new RemoveTypeAnnotationAndEqualSign(f))
      }
  }
}