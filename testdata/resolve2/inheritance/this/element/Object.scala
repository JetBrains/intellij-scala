def a = {}

object O {
  def b = {}

  println(this./* resolved: false */a)
  println(this./* offset: 29 */b)
}