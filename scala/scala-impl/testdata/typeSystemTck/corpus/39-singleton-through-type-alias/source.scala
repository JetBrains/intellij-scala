// Concept: a val typed by an ALIAS to a singleton type is that singleton. In
// scala/scala's ClassfileParser.TastyUniverse, `type SymbolTable =
// ClassfileParser.this.symbolTable.type; val symbolTable: SymbolTable`, so
// `TastyUniverse.symbolTable` and `ClassfileParser.this.symbolTable` denote the same
// object, and members selected through either path coincide: a
// `symbolTable.ClassSymbol` is a `TastyUniverse.symbolTable.Symbol`, and (via the
// import-renamed `self.symbolTable` in TastyCore) a `TastyUniverse.Symbol`.
abstract class Universe { class Symbol; class ClassSymbol extends Symbol }
abstract class TastyCore { self: TastyUniverse =>
  import self.{symbolTable => u}
  type SymbolTable <: Universe
  val symbolTable: SymbolTable
  type Symbol = u.Symbol
}
abstract class TastyUniverse extends TastyCore
abstract class ClassfileParser {
  val symbolTable: Universe
  import symbolTable._
  object TastyUniverse extends TastyUniverse {
    type SymbolTable = ClassfileParser.this.symbolTable.type
    val symbolTable: SymbolTable = ClassfileParser.this.symbolTable
  }
  val clazz: ClassSymbol
  /*ANCHOR inParser*/
}
