class Test {
  val z: Class[Boolean] = classOf[Boolean]
  object RichClass {
    def cast(x: Int) = 1
  }
  implicit def foo(x: Class[Boolean]): RichClass.type = RichClass
  z./* */cast(false)
}