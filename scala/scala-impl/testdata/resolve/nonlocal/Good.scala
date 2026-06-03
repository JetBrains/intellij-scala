package P {
  object X {
    val x = 1
    val y = 2
  }
}

package Q {
  object B {
    {
      val x = "abx"
      import P.X._
      println("x = " + <ref>x)
    }
  }
}