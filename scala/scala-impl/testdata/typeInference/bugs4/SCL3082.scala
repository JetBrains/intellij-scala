class Example {
  type PF[A, B] = PartialFunction[B, A]

  val x: PF[Int, String] = /*start*/{
    case x => x.length()
  }/*end*/
}
//PartialFunction[String, Int]