object SCL6978 {
  object R {
    def unapply(s: String): Option[String] = Some(s)
    def unapply(a: Any): Option[Any] = Some(a)
  }

  "text" match {
    case R(s) => /*start*/s/*end*/
  }
}
//String