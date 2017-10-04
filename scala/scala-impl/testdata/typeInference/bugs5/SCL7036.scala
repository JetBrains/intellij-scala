object SCL9999 {
  case class W(p: Product2[Long, String])


  W((17L, "42")) match {
    case W(p) => /*start*/p/*end*/
  }
}
//Product2[Long, String]