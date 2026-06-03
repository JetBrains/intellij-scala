trait M[-A] {
  def id: M[A]
}
def m[A]: M[A] = null
/*start*/m.id/*end*/
//M[Any]