object Main {
  case class Foo(guid: String, stuff: String*)
  val f: Foo = null
  f match {
    case Foo(guid , stuff @ _*) =>
      /*start*/stuff/*end*/
  }
}
//scala.Seq[String]