case class <caret>MyClass(param1: Char, param2: Int) {
  override val toString = param1 + param2
}

object MyClass {
  MyClass('1', 2)
  new MyClass('1', 2)
}