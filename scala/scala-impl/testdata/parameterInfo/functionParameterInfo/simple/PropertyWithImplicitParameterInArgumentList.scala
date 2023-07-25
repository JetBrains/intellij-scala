class Property {
  def foo(implicit x: Int) = 1
}

def bar(l: Long): Unit = ()

val y = new Property
bar(y.fo<caret>o)
//TEXT: l: Long, STRIKEOUT: false