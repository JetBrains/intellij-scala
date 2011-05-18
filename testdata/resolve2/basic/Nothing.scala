abstract class A {
  val x: Nothing = exit()

  x./* */asInstaceOf[String]
  x./* */hashCode
  def anyObject[T](): T
  anyObject()./* */asInstanceOf[String]
}