abstract class Q[T] {
  type t <: T
}

class User(q: Q[String]) {
  def r(v : q.t) = {
    v.<ref>toLowerCase
  }
}
