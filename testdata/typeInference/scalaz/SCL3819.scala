import scalaz._
import scalaz.Scalaz._

class MyApp[S] {
  def foo(p: Pure[({type λ[α]=State[S, α]})#λ]) = 1
  def foo(p: Int) = ""

  /*start*/foo(new Pure[({type λ[α]=State[S, α]})#λ] {
    def pure[A](a: => A) = a.state[S]
  })/*end*/
}
//Int