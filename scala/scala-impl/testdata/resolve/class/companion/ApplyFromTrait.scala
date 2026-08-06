import scala.util.Try

case class UserId private(value: Long) extends AnyVal

object UserId extends Validatable[Long, UserId]

trait Validatable[V, T] {
  def apply(value: V): Try[T] = Try {
    ???
  }
}

object Application extends App {
  val userId = UserId(10)
  userId.<ref>get
}