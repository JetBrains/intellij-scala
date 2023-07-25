class Property {
  def foo(implicit x: Int) = 1
}

val prop = new Property
pro<caret>p.foo
//NO_ELEMENTS