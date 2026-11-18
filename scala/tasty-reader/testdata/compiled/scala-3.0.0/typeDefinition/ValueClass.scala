package typeDefinition

object ValueClass {
  class ValueClass1(val x: Int) extends AnyVal

  class ValueClass2[A](val x: A) extends AnyVal

  class ValueClass3(val x: Int) extends AnyVal, Serializable
}