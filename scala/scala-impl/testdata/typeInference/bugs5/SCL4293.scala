object SCL4293 {
class foo{

}

class bar{

}

object Repro extends App {

  implicit def unused(x : Any) : bar = {
    sys.error("don't run me!")
  }

  def applyFunc(fxn: foo => bar) : foo => bar = fxn


  def doSomething(arg1 : foo => bar) {
    // maybe I should do something
  }

  doSomething(/*start*/applyFunc(x => new bar)/*end*/)   //applyFunc(x => new bar) is red though it compiles

}
}
//SCL4293.foo => SCL4293.bar