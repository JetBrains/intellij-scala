object T {
  implicit def i2s(i: Int): String = ""
}


class Z {
  protected implicit def i2s(i: Int): String = ""
}
class U extends Z {
  val xx: String = /*start*/2/*end*/
}
1
//String