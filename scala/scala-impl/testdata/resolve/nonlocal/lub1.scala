class ClassBase  {
  def foo:String
}
class Class1 extends ClassBase
class Class2 extends ClassBase

class Class3 {
  def bar(c1 : Class1, c2 : Class2) = if (goo) c1  else c2

  def r {
    bar(null, null).<ref>foo
  }
}
