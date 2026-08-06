class Z[Q, R]

abstract class Wrapper {
  val x: ((Z[Q, R]) forSome {type Q; type X}) forSome {type R}
  /*start*/x/*end*/
}
//Z[_, _]