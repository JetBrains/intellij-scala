object IntelliJBug {
  def trav[T[_] <: Traversable[_], A](t : T[A]): T[A] = exit()

  val m : Map[Int, Int] = null
  /*start*/trav(m)/*end*/
}
//Iterable[(Int, Int)]