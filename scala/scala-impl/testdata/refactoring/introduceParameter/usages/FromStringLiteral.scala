class FromStringLiteral {
  def aaa(x: Int): Unit = {
    "aa"

    "/*start*/aa/*end*/ aa"
  }

  aaa(1)
}
/*
class FromStringLiteral {
  def aaa(x: Int, param: String): Unit = {
    param

    "/*start*/" + param + "/*end*/ " + param
  }

  aaa(1, "aa")
}
*/