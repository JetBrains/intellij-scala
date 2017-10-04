trait Foo[Tag, In, Out] {
  def apply(x: In):Out
}

trait UFunc {
  //  type Impl[In, Out] = Foo[this.type, In, Out]
  def apply[In, Out](x: In)(implicit impl: Foo[this.type, In, Out]):Out =  impl(x)
  def works[In, Out](x: In)(implicit impl: Foo[this.type, In, Out]):Out =  impl(x)
}

object implicitInstance extends UFunc {

  implicit val z: Foo[this.type, Int, Double] = null

}

object ImplicitMain {

  // x inferred as nothing, should be Double
  val x = implicitInstance(3)
  // Ok: y inferred as Double, should be Double
  val y = implicitInstance.works(3)
  // this also works: if you spell out "apply" it's fine.
  val t = implicitInstance.apply(3)

  {
    // for comparison:
    implicit object baz extends Foo[implicitInstance.type, Double, Int] {
      def apply(y: Double): Int = 3
    }

    // Ok: z inferred as Int, should be Int. (baz implicit is found.)
    val z = implicitInstance(3.0)

    /*start*/(x, y, t, z)/*end*/
  }
}
//(Double, Double, Double, Int)