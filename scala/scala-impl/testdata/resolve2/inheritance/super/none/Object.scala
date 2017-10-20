def a {}
case class A

object O {
  def b {}
  case class B

  println(super./* resolved: false */a)
  println(super./* resolved: false */b)

  println(super./* resolved: false */A.getClass)
  println(classOf[super./* resolved: false */A])

  println(super./* resolved: false */B.getClass)
  println(classOf[super./* resolved: false */B])
}