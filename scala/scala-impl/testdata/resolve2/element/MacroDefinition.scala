object M {
  def f = macro /* resolved: true */fImpl
  def fImpl = 0
}
