object test {
  class S[A](as: A*)
  trait A
  case object B extends A
  case class C(c: Any) extends A
  case class CC(label: S[A])
  CC(/*start*/new S(B, C(()))/*end*/) // expected test.S[test.A], found: test.S[A]
}
//test.S[test.A]