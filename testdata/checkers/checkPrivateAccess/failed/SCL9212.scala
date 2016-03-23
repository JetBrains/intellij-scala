class A(name:String) {
  def f(a: A) = a./*ref*/name
}
//false