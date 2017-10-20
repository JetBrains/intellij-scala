trait T {
  def factorial(x: Int): Int
}
class Counter extends T {
  //      error: recursive method factorial needs result type
  def factorial(x: Int) = {
    if (x == 0) 1 else /*start*/x * factorial(x-1)/*end*/
  }
}
//Int