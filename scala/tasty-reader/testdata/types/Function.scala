package types

trait Function {
  type T1 = () => Unit

  type T2 = Int => Unit

  type T3 = (Int, Long) => Unit

  trait T4 extends (Int => Unit)
}