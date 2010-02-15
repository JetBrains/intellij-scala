trait T1 {
  def a {}
  case class A
}

trait T2 extends T1 {
  def b {}
  case class B
}

trait T3 extends T2 {
  def c {}
  case class C

  println(/* line: 2 */a)
  println(/* line: 7 */b)
  println(/* line: 12 */c)

  println(super./* line: 2 */a)
  println(super./* line: 7 */b)
  println(super./* resolved: false */c)

  println(/* line: 3 */A.getClass)
  println(classOf[/* line: 3 */A])

  println(/* line: 8 */B.getClass)
  println(classOf[/* line: 8 */B])

  println(/* line: 13 */C.getClass)
  println(classOf[/* line: 13 */C])

  println(super./* line: 3 */A.getClass)
  println(classOf[super./* line: 3 */A])

  println(super./* line: 8 */B.getClass)
  println(classOf[super./* line: 8 */B])

  println(super./* resolved: false */C.getClass)
  println(classOf[super./* resolved: false */C])
}