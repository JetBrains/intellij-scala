// Concept: a refinement member reached through a path is seen from that path once.
// `TypeOfClonedSymbol <: Symbol { type NameType = Symbol.this.NameType }`, so for
// `val clone = s.cloneSymbol` (type `s.TypeOfClonedSymbol`) `clone.NameType` is
// the refinement's `NameType` seen from `s`: `s.NameType`. The refinement's
// `Symbol.this` is the OUTER Symbol, already rewritten by the bound's asSeenFrom;
// rewriting it again from `clone` would make `clone.NameType` alias itself. Shape
// from scala/scala transform/SpecializeTypes.scala (`clone.name.decode`).
abstract class Name { def decode: String = "" }
abstract class Symbol {
  type NameType >: Null <: Name
  type TypeOfClonedSymbol >: Null <: Symbol { type NameType = Symbol.this.NameType }
  def name: NameType
  def cloneSymbol: TypeOfClonedSymbol
}
object Use {
  def decoded(s: Symbol): Unit = {
    val clone = s.cloneSymbol
    /*ANCHOR inDecoded*/
    ()
  }
}
