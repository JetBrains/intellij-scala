package types

trait Tuple {
  type T1 = (Int, Long)

  type T2 = (Int, Long, Unit)

  type T3 = scala.Tuple.Size[(Int, Long)]
}