class Z[T[_]]
class TR[Y, Z[_]]

object Wrapper {
  val e: Z[CC] forSome {type X; type CC[Y] <: TR[Y, CC[Y]]} = ???
  /*start*/e/*end*/
}

//(Z[CC]) forSome {type CC[Y] <: TR[Y, CC[Y]]}