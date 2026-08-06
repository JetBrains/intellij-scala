trait M[-A] {
  def id: M[A]
}
def m[A]: M[A] = null

val m1 = m
/*start*/m1/*end*/: M[Int]
//M[Any]