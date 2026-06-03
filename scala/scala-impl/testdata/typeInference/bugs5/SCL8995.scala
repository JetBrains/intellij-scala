trait SCL8995 {
  trait Tree
  trait Name
  abstract class SelectExtractor {
    def apply(qualifier: Tree, name: Name): Select
    def unapply(select: Select): Option[(Tree, Name)]
  }
  case class Select(qualifier: Tree, name: Name)
    extends Tree {
  }
  object Select extends SelectExtractor {} // object creation impossible, unapply not defined...

  def test(t: Tree) = t match {
    case Select(a, b) => /*start*/a/*end*/
  }
}
//SCL8995.this.Tree