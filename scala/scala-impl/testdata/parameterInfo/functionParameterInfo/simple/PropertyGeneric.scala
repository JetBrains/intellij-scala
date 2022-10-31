class Property {
  def foo[T](implicit x: Int) = 1
}

val y = new Property
y.fo<caret>o[Int]
//[T](implicit x: Int)