trait T1 {
  def a {}
  case class A
}

trait T2 extends T1 {
  def b {}
  case class B

  println(/* line: 2 */a)
  println(/* line: 7 */b)

  println(super./* line: 2 */a)
  println(super./* resolved: false */b)

  println(/* line: 3 */ A.getClass)
  println(classOf[/* line: 3 */ A])

  println(/* line: 8 */ B.getClass)
  println(classOf[/* line: 8 */ B])

  println(super./* line: 3 */A.getClass)
  println(classOf[super./* line: 3 */A])

  println(super./* resolved: false */B.getClass)
  println(classOf[super./* resolved: false */B])
}