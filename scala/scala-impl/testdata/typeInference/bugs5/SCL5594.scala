object SCL5594 {
  case class Bugje(  df: (Double, Double))

  Bugje((1,2)) match {
    case Bugje( (x,y) ) => /*start*/x/*end*/
  }
}
//Double