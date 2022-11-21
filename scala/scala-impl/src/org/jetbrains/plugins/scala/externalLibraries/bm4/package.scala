package org.jetbrains.plugins.scala.externalLibraries

import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, StubBasedExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScPattern, ScPatternArgumentList, ScTypedPattern}

package object bm4 {
  object Implicit0Pattern {
    private[this] def resolvesToImplicit0Unapply(ref: ScStableCodeReference): Boolean =
      ref.refName == "implicit0"

    def unapply(pat: ScConstructorPattern): Option[ScPattern] =
      if (pat.betterMonadicForEnabled) {
        pat match {
          case ScConstructorPattern(ref, ScPatternArgumentList(arg))
            if resolvesToImplicit0Unapply(ref) => arg.toOption
          case _ => None
        }
      } else None
  }

  /**
   * Checks that given expression is a binding inside a valid `implicit0` call (i.e. it resolves to
   * the correct synthetic method, has exactly one argument, which is type ascripted
   * term name). Returns synthetic implicit value definition to be used for implicit resolution.
   */
  object Implicit0Binding {
    def unapply(e: ScTypedPattern): Boolean =
      if (e.betterMonadicForEnabled && !e.hasOnlyStub) { // patterns in for comprehensions and case clauses should never appear in stubs
        e match {
          case ScTypedPattern(_) & Parent(Parent(Implicit0Pattern(_))) => true
          case _                                                       => false
        }
      } else false
  }
}
