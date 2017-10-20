// http://youtrack.jetbrains.net/issue/SCL-1771
trait MA[M[_], A] {
def ∗[B](f: A => M[B]): M[B]
}
(null: MA[Option, Int]).∗{ x => Some(/*start*/x/*end*/)}
//Int