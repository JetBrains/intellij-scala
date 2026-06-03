case class :::[T1, T2](x1: T1, x2: T2)
class B
val a: Int ::: Double ::: B = :::(1, :::(1d, new B))
//True