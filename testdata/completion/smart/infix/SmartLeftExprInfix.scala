class A {
  def +(x: Int): Int = x
}
val nonon: A = new A

non/*caret*/ + 55
//nonon