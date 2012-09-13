object ForStmtBug {
  def goo[T](fun: (Int,Int) => Option[T]) : Option[T] = null

  goo {
    (x, y)  => for {z <- Option(x + 1)} yield /*start*/z/*end*/ + 1
  }
}
//Int