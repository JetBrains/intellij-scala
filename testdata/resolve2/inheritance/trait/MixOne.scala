trait T {
  def a = {}
}

class C extends T {
  def b = {}
  
  println(/* line: 2 */a)
  println(/* line: 6 */b)

  println(this./* line: 2 */a)
  println(this./* line: 6 */b)

  println(super./* line: 2 */a)
  println(super./* resolved: false */b)
}