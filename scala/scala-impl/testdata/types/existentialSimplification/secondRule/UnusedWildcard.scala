class Z[Q, R]

object Wrapper {
  val x: (Z[Q, R]) forSome {type Q; type X; type R} = ???
  /*start*/x/*end*/
}
//Z[_, _]