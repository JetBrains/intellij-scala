object A {
  def foo[T <: List[String]](x : T) : T = x
  def bar(f: { def foo[T <: List[S]](x : T) : T } forSome { type S }) = 1
  def bar(s: String) = "text"

  /*start*/bar(A)/*end*/
}
//Int