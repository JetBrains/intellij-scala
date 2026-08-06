case class A(as: Any*)

A(0) match {
  case /*resolved: true, name: unapplySeq*/A(a) =>
}
