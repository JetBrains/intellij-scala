class A {
  def +:::(x: Int): Int = x
}

val go: A = new A

23 +::: g/*caret*/
//go