object TwoImports {
  object G {
    val x = 34
  }
  object Y {
    val x = 35
  }

  {
    import G.x

    {
      import Y._
      print("x = " + <ref>x)
    }
  }
}