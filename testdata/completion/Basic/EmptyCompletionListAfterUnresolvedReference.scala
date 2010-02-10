class A {
  implicit def int2Class(i : Int) : Class[_]  = null

  def foo: Nothing = unresolved  //apparently 'Nothing' is assumed

  def bar = foo.get/*caret*/  //resolved, should not
}
//