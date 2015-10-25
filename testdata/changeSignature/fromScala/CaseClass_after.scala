case class MyClass(number: Int, char: Char, b: Boolean) {
  override val toString = char + number
}

object MyClass {
  MyClass(2, '1', true)
  new MyClass(2, '1', true)
}