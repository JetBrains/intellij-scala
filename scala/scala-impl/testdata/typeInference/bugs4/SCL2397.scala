class A {
  def m3(p: String) = /*start*/p match {
    case "i" => new java.util.ArrayList[Int](2)
    case "s" => new java.util.ArrayList[String](3)
  }/*end*/
}
//util.ArrayList[_ >: Int with String]