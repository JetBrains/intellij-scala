// Concept: TYPE AVOIDANCE — a block-local `val`'s singleton type must not escape
// the inferred type of the enclosing definition. `thisTree: this.type` gives
// `X.thisTree` the type `X.type`, but `X` is local to the block, so the block's
// type (and hence the inferred member type) must widen the local singleton away to
// `Tree`. This is scalac's `packedType` (`Typer.typedBlock`): a symbol owned by the
// block may not appear in the block's type.
//
//   val foo = { val X: Tree = null; X.thisTree }   // scalac: foo: Tree
//
// IntelliJ historically left the local singleton `X.type` in place, so it escaped
// its defining scope (`gen.foo: X.type`, spuriously `Required Tree, found X.type`
// at any consumer). The term probe below pins scalac's `Tree`.
class Tree {
  def thisTree: this.type = this
}
object Repro {
  /*ANCHOR top*/
}
