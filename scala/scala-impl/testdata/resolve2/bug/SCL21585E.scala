object Repro {

  trait O1[O1_A] {
    type O1_A_Alias = O1_A
    trait I1[I1_A] {
      type I1_A_Alias = I1_A
      def foo(__DEBUG__o1_a: O1_A_Alias): Unit = ()
    }
  }

  trait O2 extends O1[Int] {
    trait I2 extends I1[String] with O1[Nothing] {
      self: X =>
      self./*resolved: true*/foo(1)
    }
  }
  trait X

}
