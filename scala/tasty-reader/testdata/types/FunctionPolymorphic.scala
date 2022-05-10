package types

trait FunctionPolymorphic {
  type T1 = [A] => Int => Unit

  type T2 = [A] => (Int, Long) => Unit

  type T3 = [A, B] => Int => Unit

  def repeated(xs: ([A] => Int => Unit)*): Unit
}