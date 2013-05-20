trait ~>[F[_], G[_]] {
  def apply[A](a: F[A]): G[A]
}

type Id[A] = A

def apply[B](f: Id ~> List, b: B, s: String): (List[B], List[String]) =
  (/*start*/f(b)/*end*/, f(s))   //  f(b) :  List[Any] doesn't conform to expected List[B]
//List[B]