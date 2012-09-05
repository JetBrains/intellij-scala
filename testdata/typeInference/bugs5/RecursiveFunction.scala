object RecursiveFunction {
class PathsTree(parent: Option[PathsTree]) {
  override def toString = /*start*/parent.map(pathsTree => pathsTree.toString)/*end*/.getOrElse("dfadf")
}
}
//Option[String]