trait IOI {
  def ioi = 2
}
trait E extends IOI {
  trait FF {
    def ioi = 1
  }

  trait F extends FF {
    def foo = {
      E.super.ioi
      "stop here"
    }
  }
  def moo {new F{}.foo}
}
object Sample extends A {
  def main(args: Array[String]) {
    new E {}.moo
  }
}