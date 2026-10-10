// Concept: re-anchor an INFERRED member type across a PROPER-SUPERCLASS owner in a
// nested cake (the scala/scala `Definitions.AnyTpe` report, SCL-21947 family).
//
// `foo` is declared in `Definitions` (self type `SymbolTable`, a PROPER subclass:
// `SymbolTable extends Definitions`); its result type is inferred from
// `NoSymbol.tpe`. Accessed as `g.foo` with `g: Global` (`Global <: SymbolTable`),
// the result must be `g.Type`.
//
// How scalac 2.13 gets there: it spells the inferred type after the USING class,
// `Definitions.this.Type` (`NoSymbol` is selected from `Definitions.this`, and
// `Symbol#tpe: SymbolTable.this.Type` seen from `Definitions.this.NoSymbol.type`
// re-anchors at `SymbolTable`, a base class of `Definitions.this` via the self type).
// `asSeenFrom(g.type, Definitions)` then matches `Definitions.this` at its first step
// (AsSeenFromMap.matchesPrefixAndClass: same class, and `g.type` has `Definitions`
// as a base class). See entry 37 and SPEC-GAPS.md §2(a), §7.
//
// IntelliJ spells the inferred type after the DECLARING class,
// `SymbolTable.this.Type`. Re-anchoring that needs SLS §3.4's "D is a subclass of C"
// reading of the this-type rule (scalac 2.10's `toPrefix`; 2.11+ match the exact
// class). ThisTypeSubstitution.doUpdateThisTypeFromClass lacked it: with the member
// owner `Definitions` != the this-type's class `SymbolTable`, it walked
// `Definitions`'s owner chain up to the enclosing object and fell off as UNMATCHED,
// keeping the raw `SymbolTable.this.Type`, a false "cannot upcast
// SymbolTable.this.Type to g.Type". Contrast entry 25, whose member type re-anchors
// through a val path rather than a bare enclosing-cake this-type.
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
