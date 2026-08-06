trait Test {
  sealed abstract class BinaryTree
  case class Leaf(value: Int) extends BinaryTree
  case class Node(tr: BinaryTree*) extends BinaryTree
  /* name: apply */Node(Leaf(5))
}