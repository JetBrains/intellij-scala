package org.example3.data

class Usage {
  val x: X = ???
  val y: Y = ???
  val z: Z = ???
}

class Usage_Local {

  val x: X = ???
  val y: Y = ???
  val z: Z = ???
}

class Usage_Local_Renamed {

  import org.example3.data.{X => X_Renamed, Z => Z_Renamed}

  val x: X_Renamed = ???
  val y: Y = ???
  val z: Z_Renamed = ???
}