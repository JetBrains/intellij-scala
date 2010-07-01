object Test
{
  class Buggy[T](f:() => T) {
    def ret = f()
  }

  def main(args:Array[String]) {
    val buggy = new Buggy(() => "a string")
    val ret = buggy.ret
    /*start*/ret/*end*/
  }

}
//String