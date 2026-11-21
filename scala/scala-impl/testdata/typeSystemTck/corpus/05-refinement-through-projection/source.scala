// Concept: substituting a type-member *refinement* through a projection type.
// The exact shape of SCL-21585: (M { type A = B })#A must reduce to B, including
// when the projection is taken through a type alias (M_A) as in HList-style
// type-level code, and when combined with another refinement via `with`.
//
// Distinct from 03: there the projected member was *inherited* and instantiated
// through a type parameter (HCons#Head); here the member is supplied by a
// *refinement* on the prefix and must be substituted through the projection.
trait M {
  type A
}

trait Base {
  type T
}
trait B extends Base {
  type T
}

class Repro

// Projection through a type parameter — the HList-style move (cf. M1#A in SCL-21585B).
type M_A[M1 <: M] = M1#A
