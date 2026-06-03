class Animation[+HH <: AnyVal](mutator: (HH) => Unit, startVal: HH, endVal: HH, time: Int)(implicit numeric: Numeric[HH]) {
  import numeric._
  /*start*/endVal - startVal/*end*/
}
//HH