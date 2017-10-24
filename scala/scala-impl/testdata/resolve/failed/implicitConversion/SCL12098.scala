import collection.generic.CanBuildFrom

class Unzip[T, CC[X] <: Traversable[X]]
(coll: CC[(T, Int)])
(implicit bf: CanBuildFrom[CC[(T, Int)], T, CC[T]]) {
  def unzipWithIndex: CC[T] = bf(coll) ++= (coll.toSeq sortBy (_._2) map (_._1)) <ref>result
}

implicit def installUnzip[T, CC[X] <: Traversable[X]]
(coll: CC[(T, Int)])
(implicit bf: CanBuildFrom[CC[(T, Int)], T, CC[T]]) = new Unzip[T, CC](coll)