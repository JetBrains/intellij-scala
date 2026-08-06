def a = {}

def foo = {
  def b = {}

  println(this./* offset: 4 */a)
  println(this./* resolved: false */b)
}