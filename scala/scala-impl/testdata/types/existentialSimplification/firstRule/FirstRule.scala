class Z[T, Q]

object Wrapper {
  val x: (Z[T, Q] forSome {type T}) forSome {type Q} = ???
  /*start*/ x /*end*/
}
//Z[_, _]