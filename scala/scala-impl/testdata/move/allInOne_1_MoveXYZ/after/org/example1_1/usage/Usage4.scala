package org.example1_1.usage

trait Usage4 {

  def myScope1(): Unit = {

    import org.example1_1.declaration.data.Z

    val zzz: Z = ???


    import org.example1_1.declaration.data.Y


    val yyy: Y = ???


    import org.example1_1.declaration.data.X






    val xxx: X = ???
  }

  def myScope2(): Unit = {
    import org.example1_1.declaration.X4
    import org.example1_1.declaration.data.Z

    val zzz: Z = ???

    import org.example1_1.declaration.X5
    import org.example1_1.declaration.data.Y


    val yyy: Y = ???


    import org.example1_1.declaration.X6
    import org.example1_1.declaration.data.X


    val xxx: X = ???
  }
}