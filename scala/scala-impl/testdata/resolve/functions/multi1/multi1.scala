class Foo[A] {
  def map(f: (A) => Int)(i: Int)(j: Int): A = null.asInstanceOf[A]

  def fooz(i: Int)(s: String) = 42

  def fooz(i: Int)(j: Int) = 43

  def puk[A, B](a:A)(b:B) = null

  def gul[A](a:A): A = null.asInstanceOf[A]

  def gul(i:Int) : Int = i

  def pal(i: Int)(s: Int) = null

  def pal(i: Int)(s: String) = null
}


object Main {
  def main(args: Array[String]) {
    val foo: Foo[String] = new Foo[String];

    val d = foo.<ref>pal(42)("abc")

    println(d)

  }


}