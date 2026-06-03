import scalaz._
import Scalaz._

object SCL5842A {
  type ErrorMonad[+A] = Either[String, A]

  def left[A](s : String) : Either[String, A] = Left(s)
  def error1[Env, A](msg : String) : Kleisli[ErrorMonad, Env, A] = left[A](msg).<ref>liftKleisli[Env]
}
