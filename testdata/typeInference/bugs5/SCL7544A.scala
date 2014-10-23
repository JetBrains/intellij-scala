object SCL7544A {
  trait Relationsz {
    type ZeroOne[Int]

    implicit def zeroOneOps[T]: ZeroOneOps[T]

    trait ZeroOneOps[T] {
      def seq(zo: ZeroOne[T]): Seq[T]
    }

    implicit class ZeroOneSyntax[T](val _zo: ZeroOne[T])(implicit ops: ZeroOneOps[T]) {
      def seq = ops.seq(_zo)
    }
  }

  class Implicits[R1 <: Relationsz, R2 <: Relationsz](r1: R1, val r2: R2) {
    import r1._
    implicit def zeroOne[T](zo: r1.ZeroOne[Int]) = /*start*/zo.seq/*end*/
  }
}
//Seq[Int]