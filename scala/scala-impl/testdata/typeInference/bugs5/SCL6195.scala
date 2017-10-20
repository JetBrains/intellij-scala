object SCL6195 {

  case class Blah(a: Int, b: Int)

  trait C {
    val c: Int
  }

  trait D {
    val d: Int
  }

  type BlahWithC = Blah with C

  def foo(x: BlahWithC) = 1
  def foo(x: Int) = "text"

  /*start*/foo(new Blah(2, 2) with C with D {
    val c = 2
    val d = 6
  })/*end*/
}
//Int