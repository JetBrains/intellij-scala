class C1 {
  def a {}
  case class A
}

class C2 extends C1 {
  def b {}
  case class B

  println(/* line: 2 */a)
  println(/* line: 7 */b)
  
  println(super./* line: 2 */a)
  println(super./* resolved: false */b)

  println(/* */ A.getClass)
  println(classOf[/* line: 3 */ A])

  println(/* */ B.getClass)
  println(classOf[/* line: 8 */ B])

  println(super./* */A.getClass)
  println(classOf[super./* line: 3 */A])

  println(super./* resolved: false */B.getClass)
  println(classOf[super./* resolved: false */B])
}