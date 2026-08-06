object G {
  object Z {
    class I
    object I
  }

  object U {
    type I = Z.I
  }

  import Z.I
  object K {
    import U._

    def foo(x: I) = 1
    def foo(x: Any) = "text"

    /*start*/foo(new I)/*end*/
  }
}
//Int