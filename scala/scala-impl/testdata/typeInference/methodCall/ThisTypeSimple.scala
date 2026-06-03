trait T {
  def me: this.type
  def t: String
}

var a: T = _
/*start*/a.me.t/*end*/
//String