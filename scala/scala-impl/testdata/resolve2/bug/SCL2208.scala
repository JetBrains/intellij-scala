trait ITest {
  def foo : Int
}

class Test(val foo : Int) extends ITest {
  def bar = /* line: 5 */foo
}