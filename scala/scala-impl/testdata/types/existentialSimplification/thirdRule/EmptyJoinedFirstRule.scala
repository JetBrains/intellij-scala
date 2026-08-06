class Z[T]
object Wrapper {
  val x: (Z[T] forSome {}) forSome {type T} = null
  /*start*/x/*end*/
}
//Z[_]