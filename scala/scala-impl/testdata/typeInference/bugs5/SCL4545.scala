trait SCL4545 {
  protected trait A {
    def fii = 1
  }
}

object test1 extends SCL4545 {
  class V extends this.type#A {
    /*start*/fii/*end*/
  }
}
//Int