def getDuplicates[T](s: Seq[T], seen: Set[T] = Set[T]()): Set[T] = s match {
  case x :: xs => if (seen contains x) getDuplicates(xs, seen) + x else /*start*/getDuplicates(xs, seen + x)/*end*/
  case _ => Set[T]()
}
//Set[T]