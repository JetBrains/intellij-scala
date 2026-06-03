package org.example1_1.usage

import org.example1_1.declaration.{X => X_Renamed, _}

trait Usage0 {

  val x1: X_Renamed = ???
  val y: Y = ???
  val z: Z = ???

  val xx: X4 = ???

  def myScope(): Unit = {
    import org.example1_1.declaration.X
    val x2: X = ???
  }
}