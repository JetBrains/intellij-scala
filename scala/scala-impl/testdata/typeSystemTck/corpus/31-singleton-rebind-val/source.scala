// Concept: override-aware singleton paths for a plain `val` override (complements
// 21's override-object shape). `get: A.this.x.type` seen from `b.type` is `b.x.type`;
// scalac's canonical `singleType` REBINDS `x` to the overriding member of the new
// prefix (Types.rebind), `B#x: String`, so `b.x.type` widens to String and
// `b.get.length` resolves. A declaration-keyed implementation that keeps `A#x`
// widens to AnyRef. The SLS gets this for free by identifying members by name in
// the prefix's type (§3.4 member bindings, §6.4). SPEC-GAPS.md §1.
trait A { val x: AnyRef; def get: x.type = x }
trait B extends A { val x: String }
object Use {
  val b: B = null
  /*ANCHOR inUse*/
}
