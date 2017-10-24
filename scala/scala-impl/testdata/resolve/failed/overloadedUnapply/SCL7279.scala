object & {
  def unapply[A](a: A): Option[(A, A)] = Some(a, a)
  def unapply[A,B](tup: Tuple2[A,B]): Option[(A, B)] = Some(tup)
}

object SCL7279 {
  1 match {
    case 1 <ref>& a =>
  }
}