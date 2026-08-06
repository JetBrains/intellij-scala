package aaa {
  import bbb.{C=>D, _}
  class A extends B<ref>B
}


package bbb {
  class BB
  class C
}