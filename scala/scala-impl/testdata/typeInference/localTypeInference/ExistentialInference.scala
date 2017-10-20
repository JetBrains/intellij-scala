object A {
  class Z
  def foo[T <: Z]( x: Class[_ <: T]*): T = null.asInstanceOf[T]
  /*start*/foo(classOf[Z])/*end*/
}
//A.Z