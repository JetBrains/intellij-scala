// Concept: lub keeps the PATH of path-dependent operands. scalac's lub walks the
// operands' base type sequences, whose elements are already seen from each
// operand's prefix, and merges same-class heads with mergePrefixAndArgs (prefixes
// lub'd). So the lub of two cake siblings selected through `global` is
// `global.Symbol` (not the declaration-site `Symbols.this.Symbol`), and the lub of
// `a.Tree` and `b.Tree` for distinct paths is the projection `G#Tree`.
// SPEC-GAPS.md §4 (the IntelliJ BoundsUtil.BaseClassInfo bug from PR #5).
trait Symbols { self: SymbolTable =>
  abstract class Symbol
  class TypeSymbol extends Symbol
  class TermSymbol extends Symbol
}
abstract class SymbolTable extends Symbols
trait G { class Tree }
trait Use {
  val global: SymbolTable
  val a: G
  val b: G
  val c: Boolean
  /*ANCHOR inUse*/
}
