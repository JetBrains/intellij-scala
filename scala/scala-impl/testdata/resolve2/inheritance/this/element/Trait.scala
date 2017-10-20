def a = {}

trait T {
  def b = {}

  println(this./* resolved: false */a)
  println(this./* offset: 28 */b)
}