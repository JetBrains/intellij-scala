1
class A {
  def foo = 1
}
object Sample extends A {
  trait Inner {
    val x = Sample.super.foo
  }
  
  val z = new Inner {}.x
}

class Simple extends A {
  trait Inner {
    val x = Simple.super.foo
  }
  
  val z = new Inner {}.x
}

object Main {
  /*start*/Sample.z + new Simple().z/*end*/
}
//Int