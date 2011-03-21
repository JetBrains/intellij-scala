package org.jetbrains.plugins.scala
package codeInspection.methodSignature

import com.intellij.codeInspection._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel Fatin
 */

class AccessorLikeMethodIsUnit extends AbstractMethodSignatureInspection(
  "AccessorLikeMethodIsUnit", "Method with accessor-like name has Unit result type") {

  @Language("HTML")
  val description =
"""Methods that follow <a href="http://en.wikipedia.org/wiki/JavaBean">JavaBean</a> naming contract for accessors are expected
to have no <a href="http://en.wikipedia.org/wiki/Side_effect_(computer_science)">side effects</a>.

However, methods with a result type of <code>Unit</code> are only executed for their side effects.

<small>* Refer to Programming in Scala, 2.3 Define some functions</small>"""

  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunction if f.hasQueryLikeName && f.hasUnitResultType && f.superMethods.isEmpty =>
      holder.registerProblem(f.nameId, getDisplayName)
  }
}