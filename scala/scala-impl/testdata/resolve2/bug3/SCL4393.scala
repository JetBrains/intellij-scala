sealed class Change[S]
abstract class Delta[S] {
  def apply(start: S): S
}
case class FullChange[S](start: S, finish: S) extends /*line: 1*/Change[S]
case class DeltaChange[S](start: S, delta: /*line: 2*/Delta[S]) extends /*line: 1*/Change[S]