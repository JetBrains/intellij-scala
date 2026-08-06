object SCL5733 {
  class A
  class B extends A
  class C extends A
  val b = new B
  val c = new C
  val seq1 = Seq((b, 1), (c, 1))
  val seq2 = Seq(b -> 1, c -> 1)
  val tuple = b -> 1
  /*start*/(seq1, seq2, tuple)/*end*/
}
/*
(Seq[(SCL5733.A, Int)], Seq[(SCL5733.A, Int)], (SCL5733.B, Int))
[Scala_2_13](Seq[(SCL5733.A, Int)], Seq[(SCL5733.A, Int)], (SCL5733.B, Int))
*/