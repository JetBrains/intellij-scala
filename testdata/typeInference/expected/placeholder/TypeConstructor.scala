// http://youtrack.jetbrains.net/issue/SCL-1771
trait MA[M[_]] {
val mint: M[Int]
}
var moa: MA[Option] = _
/*start*/moa/*end*/
//MA[Option]