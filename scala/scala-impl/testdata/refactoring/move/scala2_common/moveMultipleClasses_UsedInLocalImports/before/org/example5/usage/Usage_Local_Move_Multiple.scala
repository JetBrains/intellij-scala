package org.example5.usage

trait Usage_Local_Move_Multiple {
  println()

  import org.example5.declaration.X

  val x: X = ???

  println()

  import org.example5.declaration.Y

  val y: Y = ???
}

trait Usage_Local_Move_Multiple_ExistingImportsFromTargetPackage {
  println()

  import org.example5.declaration.X

  val x: X = ???

  println()

  import org.example5.declaration.data.A

  val a: A = ???

  import org.example5.declaration.Y

  val y: Y = ???

  println()

  import org.example5.declaration.data.B

  val b: B = ???
}
