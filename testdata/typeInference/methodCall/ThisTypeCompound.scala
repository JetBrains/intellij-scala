trait T {
  def me: this.type
}

trait W {
  def w: String
}

var a: T with W = _
/*start*/a.me.w/*end*/

//String