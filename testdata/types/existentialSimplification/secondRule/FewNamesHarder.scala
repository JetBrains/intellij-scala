abstract class A[T] {
  type X[Z]
}

val x: (A[T] forSome {type T})#X[Y] forSome {type Y; type T} = null
/*start*/x/*end*/
//(A[_])#X[_]