import scalaz._
import Scalaz._

object SCL5842 {
  type ErrorMonad[+A] = Either[String, A]

  def left[A](s : String) : Either[String, A] = Left(s)
  def error2[Env, A](msg : String) : Kleisli[({ type R[+X] = Either[String, X] })#R, Env, A] = left[A](msg).<ref>liftKleisli[Env]
}