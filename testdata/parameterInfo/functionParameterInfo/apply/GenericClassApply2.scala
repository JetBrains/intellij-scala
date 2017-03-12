trait MapLike[A, B] {
  def apply(a: A): B
}

trait Map[AA, BB] extends MapLike[AA, BB]

val intmap: Map[Int, Int] = null
intmap(<caret>)
//a: Int