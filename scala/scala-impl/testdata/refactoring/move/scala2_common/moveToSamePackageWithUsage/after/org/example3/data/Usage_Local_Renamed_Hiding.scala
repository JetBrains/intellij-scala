package org.example3.data

class Usage_Local_Renamed_Hiding {

  import org.example3._
  import org.example3.data.{X => _}

  val x: X = ??? // unresolved
  val y: Y = ???
  val z: Z = ???
}
