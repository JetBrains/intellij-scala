// Concept: how scalac SPELLS members reached through a self type, and which glb it
// picks for the self type. A member of the self type is named after the USING class
// (`Definitions.this.Symbol`), never the declaring one (`SymbolTable.this.Symbol`,
// not a path inside Definitions). SLS §3.2.5 / ch. 2 don't say self-type members are
// visible by simple name at all. The self type is `C with T` (class first), or just
// `T` when `T <: C` (Namers.SelfTypeCompleter); SLS §5.1 says only "the glb".
// SPEC-GAPS.md §7.
trait Api { type Tree }
trait Impl { self: Api =>
  /*ANCHOR inImpl*/
}
trait Definitions { self: SymbolTable =>
  def sym: Symbol = ???
  def foo = NoSymbol.tpe
  /*ANCHOR inDefinitions*/
}
trait SymbolTable extends Definitions {
  abstract class Symbol { def tpe: Type = ??? }
  object NoSymbol extends Symbol
  abstract class Type
}
