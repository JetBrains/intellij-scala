package types

trait Infix {
  class *[A, B]

  class +[A, B]

  class -[A]

  type T1 = Int * Long

  type T2 = (Int * Long) * Unit

  type T3 = (Int + Long) * Unit

  type T4 = Int * (Long + Unit)

  type T5 = Int + (Long * Unit)

  type T6 = (Int * Long) + Unit

  type T7 = -[Int]

  trait C extends (Int * Long)

  def method(xs: (Int * Long)*): Unit

  object O {
    class /[A, B]
  }

  type T8 = Infix.this.O./[Int, Long]
}