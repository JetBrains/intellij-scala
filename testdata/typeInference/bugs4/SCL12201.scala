def seqDerivedOrdering[Other[X] <: scala.collection.Seq[X], T] {
  def foo(x: Other[T]) {
    val y:Iterator[T] = /*start*/x.iterator/*end*/
  }
}
//Iterator[T]