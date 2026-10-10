// Concept: asSeenFrom of `this.type` from an UNSTABLE prefix (`mk()`). SLS §3.4's
// this-type rule answers the prefix type itself (X), which in an invariant position
// would be unsound (`mk().arr(0) = new X` would typecheck). scalac instead captures
// the prefix as a fresh existential singleton (AsSeenFromMap.captureThis):
// `Array[_ <: X with Singleton]`. Covariantly the existential simplifies away, so
// `mk().self` is just X. This matches the SLS read with §6.4's
// `{ val y = e; y.x }` rewriting + §6.11 block typing. SPEC-GAPS.md §2(c).
class X {
  def self: this.type = this
  def arr: Array[this.type] = Array(this)
}
object Use {
  def mk(): X = new X
  /*ANCHOR inUse*/
}
