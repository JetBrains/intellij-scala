implicit val v: Int = 1
{
  def f(implicit i: Int) = {}

  println(/* offset: 32 */ f)
}