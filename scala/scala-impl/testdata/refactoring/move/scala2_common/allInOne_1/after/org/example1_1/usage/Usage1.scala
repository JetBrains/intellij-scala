package org.example1_1.usage

trait Usage1 {

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
}