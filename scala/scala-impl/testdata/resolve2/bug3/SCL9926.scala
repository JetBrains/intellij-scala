object X {
  def apply[T](g: T): X[T] = null

  def apply[T](g: T = null, init: T = null): X[T] = null
}

class X[T](g: T) {}

val y = X(/*resolved: true*/init = 4)