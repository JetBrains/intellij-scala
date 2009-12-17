object NamingExtractorPattern {
  class A
  object A {
    def unapply(a: A): Option[Int] = Some(3)
  }
  new A match {
    case a@A(3) => /*start*/a/*end*/
  }
}
//A