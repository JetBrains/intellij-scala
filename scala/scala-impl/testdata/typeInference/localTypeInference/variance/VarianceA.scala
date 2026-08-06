trait M[-A] {
def id: M[A]
}
def m[A]: M[A] = null
/*start*/m.id/*end*/: M[AnyRef]  // false error: M[Nothing] does not conform to M[AnyRef]
//M[Any]