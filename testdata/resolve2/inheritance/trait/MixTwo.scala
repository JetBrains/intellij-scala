trait T1 {
  def a = {}
}

trait T2 {
  def b = {}
}

class C extends T1 with T2 {
  def c = {}
  
  println(/* line: 2 */a)
  println(/* line: 6 */b)
  println(/* line: 10 */c)

  println(this./* line: 2 */a)
  println(this./* line: 6 */b)
  println(this./* line: 10 */c)

  println(super./* line: 2 */a)
  println(super./* line: 6 */b)
  println(super./* resolved: false */c)
}