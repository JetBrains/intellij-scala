class a extends b with c {
   override def foo: Int = super.<ref>foo
}

abstract class b {
  def foo: Int
}

trait c {
  def foo: Int = 4
}