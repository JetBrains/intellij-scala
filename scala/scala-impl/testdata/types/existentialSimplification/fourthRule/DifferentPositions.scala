class A[X, +Y, -Z]

object Wrapper {
  val x: A[X, X, X] forSome {type X} = ???
  /*start*/x/*end*/
}
//A[_, Any, Nothing]