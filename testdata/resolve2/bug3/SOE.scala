abstract class PsiStackOverflowError {

  case class TGItem(tg: String) extends TreeItem[SportsmanItem] {
    def children = List()
  }

  case class SportsmanItem(user: String) extends TreeItem[SportsmanItem] {
    def children = List()
  }

  val tree = new Tree[TreeItem.Min]() {
    def setContent(trainingGroups: Iterable[String]) {
      val rootNodes = trainingGroups.map(TGItem(_))
      rootNodes.foreach(/* line: 26 */addItemRecursively)
      rootNodes.foreach(expandItemsRecursively)
    }
  }
}
object TreeItem {
  type Min = TreeItem[T forSome {type T <: TreeItem[T]}]
}
trait TreeItem[+T <: TreeItem[T]] {
  def children: Iterable[T]
}
class Tree[TI <: TreeItem[TI]] extends VaadinTree {
  def addItemRecursively(item: TI) = 1

  def addItemRecursively(s: String) {

  }
}
class VaadinTree {
  def expandItemsRecursively(startItemId: AnyRef): Boolean = false
}