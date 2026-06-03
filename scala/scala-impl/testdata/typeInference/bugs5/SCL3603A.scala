import collection.mutable.ListBuffer

type HasMap[F] = {
  def map[T](f: (F => T)): TraversableOnce[T]
}


def yourMethod[F](to: HasMap[F]) = {
  to.map(_.toString)
}


def main(args: Array[String]) {
  /*start*/yourMethod(ListBuffer(1, 2, 3))/*end*/
}
//TraversableOnce[String]