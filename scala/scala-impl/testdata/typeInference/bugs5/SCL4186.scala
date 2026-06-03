object P
{
  def a[T](x : { def b(y : { val c : T }) }, z : T): T =
  {
    object Y { val c = z }
    x.b(Y)
    z
  }

  def d() = {
    object X { def b(y : { val c : String }) { }}
    /*start*/a(X, "")/*end*/ // False error here
  }
}
//String