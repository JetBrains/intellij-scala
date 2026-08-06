object AssignmentNotImported {
  class ZZ {
    def y_=(x: Int) =  x
    def y_=(x: String) = x
  }
  object K extends ZZ {
    def y = 2
  }

  object Z {
    def y = 3
  }

  object M {
    import K.y
    /*start*/y = 2/*end*/
  }
}
//Int