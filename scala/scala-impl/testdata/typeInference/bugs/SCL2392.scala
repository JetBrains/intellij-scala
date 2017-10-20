type T <: AnyRef
val t: T = error("")
/*start*/(t: t.type, t)/*end*/
// (t.type, T)