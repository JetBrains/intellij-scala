object X {
  def apply[T](g: T): X[T] = null

  def apply[T](g: T = null, init: T = null): X[T] = null
}

class X[T](g: T) {}

val y = X(<ref>init = 4)