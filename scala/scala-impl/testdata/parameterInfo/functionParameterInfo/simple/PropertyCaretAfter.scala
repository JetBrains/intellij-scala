class Property {
  def foo(implicit x: Int) = 1
}

val y = new Property
y.foo<caret>
//implicit x: Int