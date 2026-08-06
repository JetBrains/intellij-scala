object Error {

  trait PrettyPrint[A] {
    def prettyPrint(value: A): String
  }

  case class Node(next: Option[Node])

  implicit class RichPrettyPrint[A: PrettyPrint](value: A) {
    def pp: String = implicitly[PrettyPrint[A]].prettyPrint(value)
  }

  implicit object NodeIsPrettyPrint extends PrettyPrint[Node] {
    override def prettyPrint(value: Node): String = {
      value.next.map {
        (x: Node) => x.<ref>pp
      }
      ""
    }
  }

}