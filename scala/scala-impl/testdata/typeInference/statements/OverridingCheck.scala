object OverridingCheck {
trait A {
  def foo: C = new C
}

trait B extends A {
  override def foo: G = new G
}

trait D extends  B

object Main {
  def main(args: Array[String]) {
    val d = new D {}
    /*start*/d.foo/*end*/
  }
}

class C

class G extends C {
  def foo: Int = 45
}
}
//OverridingCheck.G