class Z[T[_]]
class TR[Y, Z[_]]
val e: Z[CC] forSome {type X; type CC[Y] <: TR[Y, CC[Y]]}
/*start*/e/*end*/
//(Z[CC]) forSome {type CC <: TR[Y, CC[Y]]}