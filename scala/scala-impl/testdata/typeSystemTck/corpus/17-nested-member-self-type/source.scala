// Concept: asSeenFrom of a NESTED member's outer `this`, through a self type.
// This is the case that reaches ThisTypeSubstitution.doUpdateThisTypeFromClass's
// `BaseTypes.iterator(target).find(_.extractClass.contains(clazz))` (line 39):
// the member `get` lives in the nested `Inner`, and its result type `Tree` refers
// to the OUTER `Outer.this`. So substituting it requires walking enclosing
// classes (clazz.containingClass != null), and the walk uses BaseTypes to map the
// context class `Outer`. `Comp` reaches `Outer` only via its self type — exactly
// where a BaseTypes self-type omission would defeat the walk.
//
// Contrast entry 16, where the member is on the top-level `Api`
// (clazz.containingClass == null), so it takes the plain branch and never touches
// BaseTypes.
trait Outer {
  type Tree
  val root: Tree
  trait Inner {
    def get: Tree   // Outer.this.Tree — refers to the OUTER this
  }
  val inner: Inner
}

trait Comp { self: Outer =>
  /*ANCHOR inComp*/
}
