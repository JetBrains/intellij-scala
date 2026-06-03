import scalaz._
import scalaz.Scalaz._

class MyApp[S] {
  def foo(p: Applicative[({type λ[α]=State[S, α]})#λ]) = 1
  def foo(p: Int) = ""

  /*start*/foo(new Applicative[({type λ[α]=State[S, α]})#λ] {
    def point[A](a: => A) = a.state[S]

    def ap[A, B](fa: => State[S, A])(f: => State[S, A => B]): State[S, B] = ???
  })/*end*/
}
//Int