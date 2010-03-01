class C1 {
  private[this] def f {}
  
  class C2 {
    /* accessible: false */ f
  }
}

