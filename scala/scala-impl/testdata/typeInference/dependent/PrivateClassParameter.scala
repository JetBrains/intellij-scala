class C {
  class W

  def getZ(w: W): Int = 123
  def getZ(s: String): String = "text"
}

class A(f: C) {
  private val w = new f.W
  /*start*/f.getZ(w)/*end*/
}
//Int