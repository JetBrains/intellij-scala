package org.example3.data

import org.example3.{X, Y}
import org.example3.Z

class Usage {
  val x: X = ???
  val y: Y = ???
  val z: Z = ???
}

class Usage_Local {

  import org.example3.{X, Y}
  import org.example3.Z

  val x: X = ???
  val y: Y = ???
  val z: Z = ???
}

class Usage_Local_Renamed {

  import org.example3.{X => X_Renamed, Y}
  import org.example3.{Z => Z_Renamed}

  val x: X_Renamed = ???
  val y: Y = ???
  val z: Z_Renamed = ???
}