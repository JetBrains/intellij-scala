object SCL6549 {
  class implicitsearchbug {
    def doesntWork[Y, Z](y: Y)(implicit x: implicitsearchbug.Foo[this.type, Y, Z]) = x(y)
    def works[Y, Z](y: Y)(implicit x: implicitsearchbug.Foo[implicitInstance.type, Y, Z]) = x(y)
  }

  object implicitsearchbug {
    trait Foo[A, B, C] {
      def apply(b: B): C
    }
  }

  object implicitInstance extends implicitsearchbug {
    implicit object bar extends implicitsearchbug.Foo[this.type, Int, Double] {
      def apply(y: Int): Double = 3
    }

  }

  object ImplicitMain {

    // x inferred as nothing, should be Double
    val x = implicitInstance.doesntWork(3)
    // Ok: y inferred as Double, should be Double
    val y = implicitInstance.works(3)

    {
      // for comparison:
      implicit object baz extends implicitsearchbug.Foo[implicitInstance.type, Double, Int] {
        def apply(y: Double): Int = 3
      }

      // Ok: z inferred as Int, should be Int. (baz implicit is found.)
      val z = implicitInstance.doesntWork(3.0)

      /*start*/(x, y, z)/*end*/
    }
  }
}
//(Double, Double, Int)