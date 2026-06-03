object SCL3460 {
  trait Z {
    type A
  }
  trait G extends Z {
    implicit def foo(a: G.super[Z].A): String = "text"
  }

  object U extends G {
    type A = Long
  }

  import U._

  /*start*/1L.substring(1)/*end*/
}
//String