import scala.collection.{TraversableLike, mutable}
import scala.collection.mutable.Builder
trait MyCanBuildFrom[-From, -Elem, +To] {
  def apply(from: From): Builder[Elem, To] = null

  def apply(): Builder[Elem, To]
}
implicit def myCanBuild: MyCanBuildFrom[Seq[Option[Int]], Int, Seq[Int]] = new MyCanBuildFrom[Seq[Option[Int]], Int, Seq[Int]]() {
  override def apply(): mutable.Builder[Int, Seq[Int]] = new mutable.Builder[Int, Seq[Int]] {
    override def +=(elem: Int): this.type = this

    override def clear(): Unit = {}

    override def result():  Seq[Int] = Seq()
  }
}

def foo[A, Repr, That](options: TraversableLike[Option[A], Repr],
                       default: Option[A] = Some("".asInstanceOf[A]))
                      (implicit bf: MyCanBuildFrom[Repr, A, That]): That = bf.apply().result()

/*start*/foo(Seq(Some(1), None))/*end*/
// Seq[Int]