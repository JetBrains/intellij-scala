object ImplicitResolutionOne {
  implicit def parse(s:String) = Integer.parseInt(s)
  implicit def parse(s: ImplicitResolutionOne) = 1
  implicit def parse(s: ImplicitResolutionTwo) = 2
}

class ImplicitResolutionOne(val str:String)

object ImplicitResolutionTwo {
  implicit def parse(s: ImplicitResolutionOne) = 3
  implicit def parse(s: ImplicitResolutionTwo) = 4
}

class ImplicitResolutionTwo(val str: String)

object Caller {
  import ImplicitResolutionOne._
  import ImplicitResolutionTwo._
  def main(args: Array[String]) {
    val x:Int = /*start*/new ImplicitResolutionOne("3")/*end*/
    val y:Int = new ImplicitResolutionTwo("4")
    println(x)
    println(y)
  }
}

//Int