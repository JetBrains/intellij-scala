// Concept: singleton val-path collapse where the prefix is an OVERRIDE OBJECT
// (SCL-21947, the scala/scala `Global.gen` shape). `gen` is a `val` in SymbolTable,
// overridden as an `object` in Global with the early-init `val global:
// Global.this.type`. Reaching `gen.global.Block` binds `global`'s underlying via the
// object's override (`Global.this.type`), so `gen.global` aliases `global` and
// `gen.global.Block` conforms to `Tree` (Block extends Tree).
//
// IntelliJ's `designatorSingletonType` is `None` for objects, so the override was
// invisible and conformance failed. scalac follows `pre.memberType(sym)` (override-
// aware), and accepts it.
trait IGen {
  val global: SymbolTable
  import global._
  def blk: Block = null
}
trait NscGen extends IGen { val global: Global }
class SymbolTable {
  class Tree
  class Block extends Tree
  val gen = new IGen { val global: SymbolTable.this.type = SymbolTable.this }
}
class Global extends SymbolTable {
  override object gen extends { val global: Global.this.type = Global.this } with NscGen
}
trait Analyzer { val global: Global }
trait Typers { self: Analyzer =>
  import global._
  /*ANCHOR inTypers*/
}
