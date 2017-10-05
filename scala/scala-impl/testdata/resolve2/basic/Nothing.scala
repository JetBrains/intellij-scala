abstract class A {
  val x: Nothing = exit()

  x./* */asInstanceOf[String]
  x./* */hashCode
  def anyObject[T](): T
  anyObject()./* */asInstanceOf[String]
}