class Z[T]
val x: (Z[T] forSome {}) forSome {type T} = null
/*start*/x/*end*/
//(Z[T]) forSome {type T}