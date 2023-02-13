package types

trait Function {
  type HKT[F[_, _]]

  type T0 = HKT[Function1]

  type T1 = () => Unit

  type T2 = Int => Unit

  type T3 = (Int, Long) => Unit

  type T4 = Int => Long => Unit

  type T5 = (Int => Long) => Unit

  trait T6 extends (Int => Unit)

  def repeated(xs: (Int => Unit)*): Unit
}