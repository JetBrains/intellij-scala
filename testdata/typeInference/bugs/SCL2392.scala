type T <: AnyRef
val t: T = error("")
val tt: t.type = /*start*/t/*end*/
// t.type