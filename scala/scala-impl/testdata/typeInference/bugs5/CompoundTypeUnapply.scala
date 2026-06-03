object CompoundTypeUnapply {
  val Length: {def unapply(s: String): Option[Int]} = new {
    def unapply(s: String): Option[Int] = Some(s.length)
  }

  "text" match {
    case Length(length) => /*start*/length/*end*/
  }
}
//Int