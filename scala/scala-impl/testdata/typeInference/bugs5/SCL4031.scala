trait TypeInferenceBug {
  type TTT

  type B = Seq[TTT]

  def foo(bs: B) {
    val as1: List[(TTT, Int)] = null

    as1.foldLeft(bs) {
      case (b, (a, i : Int)) =>
        /*start*/b/*end*/
    }

  }

}
//TypeInferenceBug.this.B