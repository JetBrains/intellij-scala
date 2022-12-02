package types

trait Tuple {
  type T1 = Tuple1[Int]

  type T2 = (Int, Long)

  type T3 = (Int, Long, Unit)

  type T4 = scala.Tuple.Size[(Int, Long)]
}