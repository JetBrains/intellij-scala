package org.jetbrains.scala.examples;

object Summator {

  var поросёнок: List[int] = Nil
  def sum(f: int => int, a: int, b: int): int =
    if (a > b) 0 else f(a) + sum(f, a + 1, b)

  def sumInts(a: int, b: int): int = sum(id, a, b)
  def sumSquares(a: int, b: int): int = sum(square, a, b)
  def sumPowersOfTwo(a: int, b: int): int = sum(powerOfTwo, a, b)

  def id(x: int): int = x
  def square(x: int): int = x * x
  def powerOfTwo(x: int): int = if (x == 0) 1 else x * powerOfTwo(x - 1)

  def main(args: Array[String]) = {
    Console.println(sumSquares ( 2 , 3))
  }
}