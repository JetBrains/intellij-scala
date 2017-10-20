abstract class test {

  class CBF[-B, +T]

  implicit def foo[T]: CBF[T, Array[T]] = new CBF

  def map[B, That](x: B)(implicit cbf: CBF[B, That]): That

  class O

  class H extends O

  def x: Array[O] = /*start*/map(new H)/*end*/
}
//Array[test.this.O]