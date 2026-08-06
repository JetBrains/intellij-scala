package org.example1_1.usage

import org.example1_1.declaration._
import org.example1_1.declaration.data.{X => X_Renamed, Y => Y_Renamed, Z => Z_Renamed}


trait Usage2 {

  val x1: X_Renamed = ???
  val y1: Y_Renamed = ???
  val y2: Y_Renamed = ???
  val z1: Z_Renamed = ???
  val z2: Z_Renamed = ???

  val xx: X4 = ???

  def myScope1(): Unit = {
    import org.example1_1.declaration.data.X
    val xxx: X = ???
    import org.example1_1.declaration.data.Y
    val yyy: Y = ???
    import org.example1_1.declaration.data.Z
    val zzz: Z = ???
  }

  def myScope2(): Unit = {
    import org.example1_1.declaration.data.{Z => Z_Renamed_New}
    val zzz: Z_Renamed_New = ???

    import org.example1_1.declaration.data.{Y => Y_Renamed_New}
    val yyy: Y_Renamed_New = ???

    import org.example1_1.declaration.data.{X => X_Renamed_New}
    val xxx: X_Renamed_New = ???
  }

  def myScope3(): Unit = {

    import org.example1_1.declaration.data.{Z => Z_Renamed_New}

    val zzz: Z_Renamed_New = ???


    import org.example1_1.declaration.data.{Y => Y_Renamed_New}


    val yyy: Y_Renamed_New = ???


    import org.example1_1.declaration.data.{X => X_Renamed_New}





    val xxx: X_Renamed_New = ???
  }


  def myScope4(): Unit = {
    import org.example1_1.declaration.X4
    import org.example1_1.declaration.data.{Z => Z_Renamed_New}


    val zzz: Z_Renamed_New = ???

    import org.example1_1.declaration.X5
    import org.example1_1.declaration.data.{Y => Y_Renamed_New}


    val yyy: Y_Renamed_New = ???


    import org.example1_1.declaration.X6
    import org.example1_1.declaration.data.{X => X_Renamed_New}


    val xxx: X_Renamed_New = ???
  }
}