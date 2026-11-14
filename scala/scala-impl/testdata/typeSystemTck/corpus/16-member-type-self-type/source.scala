// Concept: member type resolution / asSeenFrom through a SELF TYPE (SCL-21947).
// `Impl` has self-type `Api`, so inside `Impl` the members of `Api` are in scope
// on `this`. The type of `root` is `Api`'s abstract `Tree`; accessed via
// `Impl.this`, asSeenFrom must rewrite the prefix `Api.this` -> `Impl.this`,
// yielding `Impl.this.Tree`. That rewrite goes through ThisTypeSubstitution, which
// (on a buggy BaseTypes) cannot find `Api` among `Impl.this`'s base types because
// the self-type is omitted — so it leaves the prefix as `Api.this.Tree`.
//
// The term probes below read the *inferred type* of such accesses; the goldens
// are scalac's answer.
trait Api {
  type Tree
  def root: Tree
}

trait Impl { self: Api =>
  /*ANCHOR inImpl*/
}
