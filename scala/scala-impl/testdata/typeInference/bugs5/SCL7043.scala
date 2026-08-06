abstract class C[T] {
  def lee : T
}

class CE[T <: Enumeration](val enum: T) extends C[T#Value] {
  def foo(t: T#Value) = 1
  def foo(s: String) = "text"

  /*start*/foo(enum.values.toList(0))/*end*/
  def lee = enum.values.toList(0)
}
//Int