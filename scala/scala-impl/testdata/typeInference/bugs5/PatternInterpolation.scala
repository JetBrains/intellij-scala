class TestRegex {
  implicit class A(s : StringContext) {
    object g {
      def unapplySeq(s: String): Option[Seq[String]] = None
    }
  }

  "text" match {
    case g"$a + $b + ${c: String}" =>
      /*start*/(a, b, c)/*end*/
  }
}
//(String, String, String)