object IntelliJBug {
  def trav[T[_] <: Traversable[_], A](t : T[A]): T[A] = ???

  val m : Map[Int, Int] = null
  /*start*/trav(m)/*end*/
}
/*
Iterable[(Int, Int)]
[Scala_2_13]Map[Int, Int]
*/