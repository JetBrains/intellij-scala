trait Analyzer extends Typers{
  val global: Global
}

trait Typers {
  self: Analyzer =>
  sealed class B[+T]

  case class A[+T](value: T) extends B[T]
}

class Global {
  lazy val analyzer = new {val global: Global.this.type = Global.this} with Analyzer
}

abstract class D {
  val global: Global

  import global._
  import analyzer._
  val a: B[Int] = new B[Int]
  val b: A[Int] = /*start*/A(1)/*end*/
  a match {
    case A(r) => r
  }
}
//D.this.global.analyzer.A[Int]