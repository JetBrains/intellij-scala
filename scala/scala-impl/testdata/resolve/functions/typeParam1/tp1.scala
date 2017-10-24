class Foo[A] {
  def map(f: (A) => Int)(i: Int)(j: Int): A = null.asInstanceOf[A]

  def fooz(i: Int)(s: String) = 42

  def fooz(i: Int)(j: Int) = 43

  def puk[A, B](a:A)(b:B) = null

  def gul[A](a:A): A = null.asInstanceOf[A]

  def gul(i:Int) : Int = i

  def pal(i: Int)(s: String) = null
  def pal(i: String)(j: Int) = null

}


object Main {
  def main(args: Array[String]) {
    val foo: Foo[String] = new Foo[String];

//     println(foo.gul[Int](13))

    val d = foo.pal(42)

    println(d)

    //      foo.map(s => s)(32)(32)

//    foo.puk[Int, Int](42)

    foo.<ref>gul("54")

//    4.asInstanceOf[Foo]

//    foo.fooz(239)(45)
//    foo.fooz(42)[Int](53)

  }
}

