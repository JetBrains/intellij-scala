package org.example1_1.usage

import org.example1_1.declaration._
import org.example1_1.declaration.data.{X => X_Renamed}

trait Usage0 {

  val x1: X_Renamed = ???
  val y: Y = ???
  val z: Z = ???

  val xx: X4 = ???

  def myScope(): Unit = {
    import org.example1_1.declaration.data.X
    val x2: X = ???
  }
}