class Property {
  def foo(using x: Int) = 1
}

def bar(l: Long): Unit = ()

val y = new Property
bar(y.fo<caret>o)
//TEXT: l: Long, STRIKEOUT: false