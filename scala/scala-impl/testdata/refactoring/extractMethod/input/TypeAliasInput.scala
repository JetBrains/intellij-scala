class TypeAliasInput {
  def foo {
    type x = String
    val z: x = ""
    /*start*/
    val y = z
    /*end*/
    y
  }
}
/*
class TypeAliasInput {
  def foo {
    type x = String
    val z: x = ""

    val y: String = testMethodName(z)

    y
  }

  def testMethodName(z: String): String = {
    val y = z
    y
  }
}
*/