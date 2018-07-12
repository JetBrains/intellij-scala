class Property {
  def foo(x: Int) = 1
}

def foo(i: Int): Unit = ()

val y = new Property
foo(y.fo<caret>o)
//i: Int