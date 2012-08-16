object SCL4576 {
  import java.util.Arrays.{asList, _}

  val zz = 1

  def foo() {
    val x = 1
    /*start*/
    //some text
    print(x + zz)
    /*end*/
  }
}
/*
object SCL4576 {
  import java.util.Arrays.{asList, _}

  val zz = 1

  def foo() {
    val x = 1
    /*start*/
    testMethodName(x)
    /*end*/
  }

  def testMethodName(x: Int) {
    //some text
    print(x + zz)
  }
}
*/