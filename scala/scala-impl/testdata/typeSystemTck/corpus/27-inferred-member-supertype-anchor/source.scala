// Concept: re-anchor an INFERRED member type across a PROPER-SUPERCLASS owner in a
// nested cake — scalac's AsSeenFromMap.toPrefix first (non-skip) branch.
//
// `foo`'s result type is INFERRED as `SymbolTable.this.Type` (from `NoSymbol.tpe`).
// `foo` is declared in `Definitions`, a PROPER superclass of `SymbolTable`
// (`SymbolTable extends Definitions`); both are members of the enclosing corpus
// object. Accessed as `g.foo` with `g: Global` (`Global <: SymbolTable`), asSeenFrom
// must yield `g.Type`: scalac's `toPrefix` returns the prefix `g.type` directly at its
// FIRST step, because `SymbolTable <: Definitions` and `Global <: SymbolTable`, WITHOUT
// ever consulting `pre baseType Definitions` (whose prefix is the enclosing object,
// not the root).
//
// IntelliJ's ThisTypeSubstitution.doUpdateThisTypeFromClass lacked that first-branch
// check: with the member owner `Definitions` != the this-type's class `SymbolTable`,
// it walked `Definitions`'s owner chain up to the enclosing object and fell off as
// UNMATCHED, keeping the raw `SymbolTable.this.Type` — a false "cannot upcast
// SymbolTable.this.Type to g.Type" (the scala/scala `Definitions.AnyTpe` report,
// SCL-21947 family). Contrast entry 25, whose member type re-anchors through a val
// path rather than a bare enclosing-cake this-type.
trait Definitions {
  self: SymbolTable =>
  def foo = NoSymbol.tpe
}
trait SymbolTable extends Definitions {
  abstract class Symbol { def tpe: Type = ??? }
  object NoSymbol extends Symbol
  abstract class Type
}
trait Global extends SymbolTable

trait Use {
  val g: Global = ???
  val gfoo = g.foo
  /*ANCHOR inUse*/
}
