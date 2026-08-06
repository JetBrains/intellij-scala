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

object test {
  import Expressions._
  f./*line: 2*/*(0d)
}