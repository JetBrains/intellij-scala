object test {
  class C
  class RichC {
    def apply[X](x: String) = "bang!"
  }
  implicit def RichC(c: C): RichC = null

  val richC: RichC = null
  val c: C = null

  /*start*/(richC[Int]("abc"), c("abc"), c[Int](" "))/*end*/
}
// (String, String, String)