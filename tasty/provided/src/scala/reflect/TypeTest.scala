package scala.reflect

trait TypeTest[-S, T] {
  def unapply(x: S): Option[x.type with T]
}
