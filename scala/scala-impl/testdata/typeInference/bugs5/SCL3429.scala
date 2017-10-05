class Boot(val x: Boolean) {
  def foo: Boolean = false
  var m: Boolean = false
  type F = () => Boolean
  val X: (F, F,  F) = /*start*/(m _, foo _, x _)/*end*/
}
()
//(() => Boolean, () => Boolean, () => Boolean)