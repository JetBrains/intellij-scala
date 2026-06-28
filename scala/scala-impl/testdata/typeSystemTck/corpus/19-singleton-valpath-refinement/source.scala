// Concept: singleton VAL-PATH collapse through a COMPOUND REFINEMENT (SCL-21947,
// the scala/scala `Infer.inferTypedPattern` shape).
//
// `analyzer` is `new { val global: Global.this.type = Global.this } with Analyzer`,
// so its type carries the refinement `{ val global: Global.this.type }`. Hence the
// path `global.analyzer.global` aliases the enclosing `global` (the refined member
// has singleton type `Global.this.type`), and `global.analyzer.global.Type` is the
// SAME type as `global.Type`.
//
// IntelliJ resolved the `global` member of `analyzer` to the abstract
// `Analyzer#global: Global`, dropping the refinement's singleton, so the chained
// projection never collapsed and conformance of the two `Type` projections failed —
// a spurious "type mismatch". scalac treats them as equal (both directions hold).
trait Typers { self: Analyzer =>
  import global._
  abstract class Typer { def applyTypeToWildcards(tp: Type): Type = tp }
}
trait Infer { self: Analyzer =>
  import global._
  class Inferencer {
    /*ANCHOR inInfer*/
  }
}
trait Analyzer extends Typers with Infer { val global: Global }
class Global {
  type Type
  lazy val analyzer = new { val global: Global.this.type = Global.this } with Analyzer
  object typer extends analyzer.Typer
}
