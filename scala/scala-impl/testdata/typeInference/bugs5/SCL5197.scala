val x: Any = Some(1)
x match {
  case Some(z: Int) =>
    /*start*/z/*end*/
}
//Int