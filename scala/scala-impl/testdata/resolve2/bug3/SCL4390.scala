case class F()
case class T()

object F {
  implicit def FtoT(f: F): T = T()
  implicit def pimpTest(x: Test.type) = new {
    def test(x: F) {}
  }
}

object Test {

  def doSomething(x: T) {}

  doSomething(F()) // this compiles in Scala 2.9.1
  this./*resolved: false*/test(F())   // this fails in Scala 2.9.1, but IDEA resolves implicit

}