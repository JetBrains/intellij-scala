def f(a: Int) = {}
{
  def f(a: Int, b: Int) = {}
  println(/* offset: 27, applicable: false */ f(1))
  println(/* offset: 27 */ f(1, 2))
}
