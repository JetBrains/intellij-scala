class H

trait K[T <: H] {
  def get: T
}

val x: K[_] = new K[Int]
val a: H = x.get
//False