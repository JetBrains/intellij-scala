import scala.collection.SeqLike
object Test {
  object :+ {
    def unapply[T, Coll <: SeqLike[T, Coll]](t: Coll with SeqLike[T, Coll]): Option[(Coll, T)] = ???
  }
  val init :+ last = List("", "")
  init: List[String] // okay
  /*start*/last/*end*/

  val unapply = :+.unapply(List[String]())
  unapply : Option[(Seq[String], String)] // okay
}
//String