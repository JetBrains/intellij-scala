trait T {
  def me: this.type
}

trait W {
  def w: String
}

var a: T with W = _
/*start*/a.me.w/*end*/
// This test is currently failing, as a.me is inferred as T.type rather than (T with W).type

//String