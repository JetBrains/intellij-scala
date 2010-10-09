trait Foo {
  def *(a: Double) = "Foo#*"
}

def f: Foo = new Foo {}

trait Expr {
  def *(a: Any) = "Expr#*"
}

object Expressions {
  implicit def FooToExpr(d: Foo): Expr = new Expr {}
}
object Expressions2 {
  implicit def FooToExpr(d: Foo): Expr = new Expr {}
}

object test {
  import Expressions._
  import Expressions2.FooToExpr
  f./*line: 6*/*("")
}