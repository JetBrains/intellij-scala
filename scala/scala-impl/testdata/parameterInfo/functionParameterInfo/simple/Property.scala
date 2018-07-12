class Property {
  def foo(implicit x: Int) = 1
}

val y = new Property
y.fo<caret>o
//implicit x: Int