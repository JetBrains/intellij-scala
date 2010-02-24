implicit val a: Int = 1;
{
  def f(implicit i: Int) = {}

  implicit val b: Int = 2

  println(/* offset: 32, valid: false */ f)
}