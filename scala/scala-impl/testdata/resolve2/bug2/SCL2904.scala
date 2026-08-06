trait T {
  def t = this
}
trait U {
  def u = this
}
class C(f: T => T)(g: U => U) {
  def this() = this(_./*resolved: true*/t) (u => u./*resolved: true*/u)
}

new C(_./*resolved: true*/t) (u => u./*resolved: true*/u)