package typeDefinition

object ImplicitClass {
  implicit class ImplicitClass(val x: Int)

  implicit class ImplicitClassAnyVal(val x: Int) extends AnyVal
}