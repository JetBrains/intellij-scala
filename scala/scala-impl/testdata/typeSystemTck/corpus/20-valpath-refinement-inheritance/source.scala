// Concept: singleton VAL-PATH collapse through a compound refinement, but the
// conformance crosses INHERITANCE (SCL-21947, the `gen.global.Block` shape).
//
// `gen` is `new { val global: Global.this.type = Global.this } with Gen`, so its
// type carries the refinement `{ val global: Global.this.type }` and the path
// `gen.global` aliases the enclosing `global`. With `class Block extends Tree`,
// `gen.global.Block` therefore conforms to `Tree` (= `global.Tree` via the import).
//
// The same-member case (`gen.global.Tree <:< Tree`) is the entry-19 shape; here the
// across-inheritance case (`gen.global.Block <:< Tree`) is the addition. IntelliJ
// reported a false "type mismatch" because it never collapsed `gen.global` to
// `global` on the conformance's right-hand side. scalac accepts both.
trait Gen { val global: Global }
trait Typers { self: Analyzer =>
  import global._
  /*ANCHOR inTypers*/
}
trait Analyzer extends Typers { val global: Global }
class Global {
  class Tree
  class Block extends Tree
  lazy val gen = new { val global: Global.this.type = Global.this } with Gen
}
