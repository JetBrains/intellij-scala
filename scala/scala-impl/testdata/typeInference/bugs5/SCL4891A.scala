object Test {
  sealed trait _MyId
  val x: Long {type Tag = _MyId} = exit()
  /*start*/x + 1/*end*/
}
//Long