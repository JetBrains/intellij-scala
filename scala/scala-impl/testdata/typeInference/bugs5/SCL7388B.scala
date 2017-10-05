object SCL7388B {
  class Aa
  class B

  def goo(x: Aa): Int = 1
  def goo(x: B): String = "2"

  implicit def i2s(i: Int): String = i.toString

  val s: String = /*start*/goo(new Aa)/*end*/
}
//String