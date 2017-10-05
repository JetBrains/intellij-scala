object SCL5681 extends App {
  implicit class Letter(val c: Char) extends AnyVal {
    def min(other: Letter): Letter = this.c min other.c
  }

  val z: Char = '1'




  implicit class A(val s: String) extends AnyVal {
    def foo(x: BigInt): Int = 1
  }

  implicit class B(val s: String) extends AnyVal {
    def foo(x : Int): Int = 2
  }

  /*start*/(z min z, "text".foo(123))/*end*/
}
//(Char, Int)