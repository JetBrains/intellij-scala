object ImplicitVsNone {
  def parse: Int = 123
  def parse[T : ClassManifest]:T = null.asInstanceOf[T]

  /*start*/ImplicitVsNone.parse/*end*/
}
//Int