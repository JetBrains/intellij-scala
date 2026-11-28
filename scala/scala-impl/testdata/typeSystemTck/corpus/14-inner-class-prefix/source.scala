// Concept: prefix substitution (asSeenFrom of the *prefix*, not the args). Inner
// classes are path-dependent: distinct stable prefixes give distinct, mutually
// non-conforming types, both bounded above by the projection Outer#Inner.
trait Outer {
  class Inner
}
object O1 extends Outer
object O2 extends Outer
