class Property {
  def foo(implicit x: Int) = 1
  def ++ (foo: Int) = new Property
}

val y = new Property
y ++ y.fo<caret>o
//TEXT: foo: Int, STRIKEOUT: false