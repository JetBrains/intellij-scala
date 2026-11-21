// Concept: the scala/scala compiler cake shape that triggered a StackOverflowError
// in ThisTypeSubstitution (intellij-scala scala-typesystem-tck, Infer.scala repro) —
// FUSED-SUBST-SCALAC.md's flagged-but-unconfirmed "cross-symbol pump". Widened from
// two prior (non-reproducing) synthetic attempts: more self-typed layers between
// Infer and Analyzer (mirroring Contexts/Namers/Typers all self-typed on Analyzer),
// and multiple sub-components each re-deriving `global` with a DIFFERENT anchor
// (Analyzer.this vs Global.this vs a further-nested object), so overload resolution
// on a call reached through several of them concatenates several distinct
// this-substitutions into one fused chain (`followed()`), as the real crash's dump
// showed 4 chain elements alternating `asSeenFrom Global` / `asSeenFrom Analyzer`.
trait Contexts { self: Analyzer =>
  val global: Global
  object rootCtx extends {
    val global: Contexts.this.global.type = Contexts.this.global
  } with AnyRef {
    def ctxFoo(x: Int): Int = x
    def ctxFoo(x: String): String = x
  }
}
trait Namers { self: Analyzer =>
  object namerFactory extends {
    val global: Namers.this.global.type = Namers.this.global
  } with AnyRef {
    def namerFoo(x: Int): Int = x
    def namerFoo(x: String): String = x
  }
}
trait Typers { self: Analyzer =>
  object typerFactory extends {
    val global: Typers.this.global.type = Typers.this.global
  } with AnyRef {
    def typerFoo(x: Int): Int = x
    def typerFoo(x: String): String = x
  }
}
trait Infer { self: Analyzer =>
  def foo(x: Int): Int = x
  def foo(x: String): String = x
  def useGlobal: Any =
    self.rootCtx.global.analyzer.foo(
      self.rootCtx.ctxFoo(self.namerFactory.namerFoo(self.typerFactory.typerFoo(1))))
  /*ANCHOR inInfer*/
}
trait Analyzer extends Contexts with Namers with Typers with Infer {
  val global: Global
}
abstract class Global { g =>
  lazy val analyzer: Analyzer = new {
    val global: Global.this.type = Global.this
  } with Analyzer
}
