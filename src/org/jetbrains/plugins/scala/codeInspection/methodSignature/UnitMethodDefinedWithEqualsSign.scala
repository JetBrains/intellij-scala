package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import quickfix.RemoveEqualsSign

/**
 * Pavel Fatin
 */

class UnitMethodDefinedWithEqualsSign extends AbstractInspection(
  "UnitMethodDefinedWithEqualsSign", "Method with Unit result type defined with equals sign") {

  @Language("HTML")
  val description =
"""Methods with a result type of <code>Unit</code> are only executed for their <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

A better way to express such methods is to leave off the equals sign, and enclose
the body of the method in curly braces.

In this form, the method looks like a <dfn>procedure</dfn>, a method that is executed only
for its side effects:

<pre><code>  <span style="color:#808080">// excessive clutter, looks like a function</span><br>  <strong style="color:#000080">def</strong> close() = { println("closed") }
  <span style="color:#808080">// may accidentally change its result type after changes in body</span><br>  <strong style="color:#000080">def</strong> close() = { file.delete() } <span style="color:#808080">// method result type is <code>Boolean</code> now</span>
  <span style="color:#808080">// concise form, side-effect is clearly stated, result type is always <code>Unit</code></span><br>  <strong style="color:#000080">def</strong> close() { file.delete() }</code></pre>
<small>* Refer to Programming in Scala, 4.1 Classes, fields, and methods</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDefinition if !f.hasExplicitType && f.hasUnitResultType =>
      f.assignment.foreach { assignment =>
        holder.registerProblem(assignment, getDisplayName, new RemoveEqualsSign(f))
      }
  }
}