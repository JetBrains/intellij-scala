// Concept: memberType of a member declared on a SUPERCLASS of an inner class,
// whose type comes from a SIBLING cake slice via the self type, accessed through
// a val path — the scala/scala Trees shape (x.symbol where x: global.ValDef).
//
// asSeenFrom must re-anchor the member's `Trees.this`-spelled type onto the
// concrete path: `Trees.this.Symbol` seen from `x.type` at class `Tree` is
// `HasGlobal.this.global.Symbol` (scalac's thisTypeAsSeen lockstep climb:
// (x.type baseType Tree).prefix = global.type, then matchesPrefixAndClass
// succeeds at Trees since Global inherits Trees).
//
// This shape falsified IntelliJ's blanket first-match-wins chain consumption
// (intellij-scala scala-typesystem-tck 64a3f1f197): a declaration-side hop's
// identity answer (Trees.this) must not starve the use-site element that
// carries the real anchor.
trait Symbols { self: SymbolTable =>
  class Symbol
}
trait Trees { self: SymbolTable =>
  abstract class Tree { def symbol: Symbol = ??? }
  class ValOrDefDef extends Tree
  class ValDef extends ValOrDefDef
}
abstract class SymbolTable extends Symbols with Trees
class Global extends SymbolTable

trait HasGlobal {
  val global: Global
  val x: global.ValDef = ???
  /*ANCHOR inHasGlobal*/
}
