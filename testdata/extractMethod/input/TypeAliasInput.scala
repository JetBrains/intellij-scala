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
  def testMethodName(z: String): String = {
    val y = z
    y
  }

  def foo {
    type x = String
    val z: x = ""
    /*start*/
    val y: String = testMethodName(z)
    /*end*/
    y
  }
}
*/