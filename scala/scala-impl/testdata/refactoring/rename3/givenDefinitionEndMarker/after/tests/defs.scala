package tests

trait Ord[T]:
  def compare(x: T, y: T): Int
  extension (x: T) def < (y: T) = compare(x, y) < 0
  extension (x: T) def > (y: T) = compare(x, y) > 0

object Givens {
  given NameAfterRename: Ord[Int] with
    def compare(x: Int, y: Int) =
      if x < y then -1 else if x > y then +1 else 0
  end NameAfterRename
}

def foo[T](i: T, j: T)(using ord: Ord[T]) =
  ord.compare(i, j)

import Givens.NameAfterRename

def test(): Unit =
  foo(1, 2)
