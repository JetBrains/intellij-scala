object P {
  val x = 34
}

object Y {
  val x = 44

  {
    import P.x
    <ref>x
  }
}