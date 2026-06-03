package org.example1_1.usage

import org.example1_1.declaration._
import org.example1_1.declaration.data.{X => X_Renamed}
import org.example1_1.declaration.{Y => Y_Renamed, Z => Z_Renamed}





trait Usage2 {

  val x1: X_Renamed = ???
  val y1: Y = ???
  val y2: Y_Renamed = ???
  val z1: Z = ???
  val z2: Z_Renamed = ???

  val xx: X4 = ???

  def myScope1(): Unit = {
    import org.example1_1.declaration.data.X
    val xxx: X = ???
    import org.example1_1.declaration.Y
    val yyy: Y = ???
    import org.example1_1.declaration.Z
    val zzz: Z = ???
  }

  def myScope2(): Unit = {
    import org.example1_1.declaration.{Z => Z_Renamed_New}
    val zzz: Z_Renamed_New = ???

    import org.example1_1.declaration.{Y => Y_Renamed_New}
    val yyy: Y_Renamed_New = ???

    import org.example1_1.declaration.data.{X => X_Renamed_New}
    val xxx: X_Renamed_New = ???
  }

  def myScope3(): Unit = {

    import org.example1_1.declaration.{Z => Z_Renamed_New}

    val zzz: Z_Renamed_New = ???


    import org.example1_1.declaration.{Y => Y_Renamed_New}


    val yyy: Y_Renamed_New = ???


    import org.example1_1.declaration.data.{X => X_Renamed_New}





    val xxx: X_Renamed_New = ???
  }


  def myScope4(): Unit = {

    import org.example1_1.declaration.{Z => Z_Renamed_New}
    import org.example1_1.declaration.X4


    val zzz: Z_Renamed_New = ???


    import org.example1_1.declaration.{Y => Y_Renamed_New}
    import org.example1_1.declaration.X5


    val yyy: Y_Renamed_New = ???


    import org.example1_1.declaration.X6
    import org.example1_1.declaration.data.{X => X_Renamed_New}


    val xxx: X_Renamed_New = ???
  }
}