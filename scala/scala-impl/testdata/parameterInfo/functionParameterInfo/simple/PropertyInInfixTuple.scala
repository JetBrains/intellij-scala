class Property {
  def foo(implicit x: Int) = 1
  def ++ (foo: Int, baz: Int) = new Property
}

val y = new Property
y ++ (y.fo<caret>o, 42)
//foo: Int, baz: Int