class Generic[T, S] {
  def foo<caret>(t: S): S = t
}

class ChildString extends Generic[String, String] {
  override def foo(t: String): String = super.foo(t)
}

class ChildSeq[T] extends Generic[Seq[T], List[T]] {
  override def foo(t: List[T]): List[T] = super.foo(t)
}