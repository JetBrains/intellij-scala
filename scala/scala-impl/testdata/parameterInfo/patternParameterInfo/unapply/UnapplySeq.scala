import scala.xml._

object UnapplySeq {

  def main(args : Array[String]) {
    val root = <root>content</root>;

    root match {
      case Elem(_, "root", <caret>_, _, child @_*) => Console.print(child)
    }
  }
}
//String, String, MetaData, NamespaceBinding, Seq[Node]