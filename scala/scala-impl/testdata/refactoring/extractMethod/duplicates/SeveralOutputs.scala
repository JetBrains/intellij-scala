object SeveralOutputs {
  def foo(i: Int) {
    val q = i
/*start*/
    println(q)
    val z = q + 1
    val zz = q + 2
    var zzz = q + 3
/*end*/

    zzz + zz + z
  }

  def foofoo(y: Int) {
    println(y)
    val x = y + 1
    val xx = y + 2
    var xxx = y + 3
  }
}
/*
object SeveralOutputs {
  def foo(i: Int) {
    val q = i

    val testMethodNameResult: (Int, Int, Int) = testMethodName(q)
    val z: Int = testMethodNameResult._1
    val zz: Int = testMethodNameResult._2
    var zzz: Int = testMethodNameResult._3


    zzz + zz + z
  }

  def testMethodName(q: Int): (Int, Int, Int) = {
    println(q)
    val z = q + 1
    val zz = q + 2
    var zzz = q + 3
    (z, zz, zzz)
  }

  def foofoo(y: Int) {
    val testMethodNameResult: (Int, Int, Int) = testMethodName(y)
    val z: Int = testMethodNameResult._1
    val zz: Int = testMethodNameResult._2
    var zzz: Int = testMethodNameResult._3
  }
}
*/