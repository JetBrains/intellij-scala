class Generic[T, S] {
  def foo(t: T): T = t
}

class ChildString extends Generic[String, String] {
  override def foo(t: String): String = super.foo(t)
}

class ChildSeq[T] extends Generic[Seq[T], List[T]] {
  override def foo(t: Seq[T]): Seq[T] = super.foo(t)
}