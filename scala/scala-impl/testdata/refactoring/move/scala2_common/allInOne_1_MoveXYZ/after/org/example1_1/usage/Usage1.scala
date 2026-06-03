package org.example1_1.usage

trait Usage1 {

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
}