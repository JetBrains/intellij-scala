trait T1 {
  class C
  object O
  type A = Int
  case class CC
  val v1: String = ""
  var v2: String = ""
  def f = {}
}

trait T2 {
  self: T1 =>

  println(classOf[/* line: 2 */C])
  println(/* line: 3 */O.getClass)
  println(classOf[/* line: 4 */A])
  println(/* line: 5 */CC.getClass)
  println(classOf[/* line: 5 */CC])
  println(/* line: 6 */v1.getClass)
  println(/* line: 7 */v2.getClass)
  println(/* line: 8 */f)
}