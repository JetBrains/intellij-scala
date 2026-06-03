package org.example2.usage

//
// SIMPLE
//
class Usage_Simple_1 {
  import org.example2.declaration.U, org.example2.declaration.data.A

  val u: U = ???

  val a: A = ???
}
class Usage_Simple_2 {
  import org.example2.declaration.{U, Y}, org.example2.declaration.data.{A, B}

  val u: U = ???
  val y: Y = ???

  val a: A = ???
  val b: B = ???
}
class Usage_Simple_3 {
  import org.example2.declaration.{U, Y, Z}, org.example2.declaration.data.{A, B, C}

  val u: U = ???
  val y: Y = ???
  val z: Z = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}

//
// SIMPLE SEPARATE IMPORTS
//
class Usage_Simple_SeparateImports_2 {
  import org.example2.declaration.U, org.example2.declaration.data.{A, B, C}, org.example2.declaration.Y

  val u: U = ???
  val y: Y = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}

class Usage_Simple_SeparateImports_3 {
  import org.example2.declaration.U, org.example2.declaration.{Y, Z}, org.example2.declaration.data.{A, B, C}

  val u: U = ???
  val y: Y = ???
  val z: Z = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}

//
// RENAMED
//
class Usage_Renamed_1 {
  import org.example2.declaration.{U => U_Renamed1}, org.example2.declaration.data.{A, B, C}

  val u: U_Renamed1 = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_2 {
  import org.example2.declaration.{Y, U => U_Renamed2}, org.example2.declaration.data.{A, B, C}

  val u: U_Renamed2 = ???
  val y: Y = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_3 {
  import org.example2.declaration.{Y, Z, U => U_Renamed3}, org.example2.declaration.data.{A, B, C}

  val u: U_Renamed3 = ???
  val y: Y = ???
  val z: Z = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}

//
// RENAMED HIDDEN
//
class Usage_Renamed_Hidden_1 {
  import org.example2.declaration.{U => _}, org.example2.declaration.data.{A, B, C}

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_Hidden_2 {
  import org.example2.declaration.{Y, U => _}, org.example2.declaration.data.{A, B, C}

  val y: Y = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_Hidden_3 {
  import org.example2.declaration.{Y, Z, U => _}, org.example2.declaration.data.{A, B, C}

  val y: Y = ???
  val z: Z = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}

//
// RENAMED SEPARATE IMPORTS
//
class Usage_Renamed_SeparateImports_2 {
  import org.example2.declaration.Y, org.example2.declaration.{U => U_Renamed2}, org.example2.declaration.data.{A, B, C}

  val u: U_Renamed2 = ???
  val y: Y = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_SeparateImports_3 {
  import org.example2.declaration.{Y, Z}, org.example2.declaration.{U => U_Renamed3}, org.example2.declaration.data.{A, B, C}

  val u: U_Renamed3 = ???
  val y: Y = ???
  val z: Z = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}

//
// RENAMED HIDDEN SEPARATE IMPORTS
//
class Usage_Renamed_Hidden_SeparateImports_2 {
  import org.example2.declaration.Y, org.example2.declaration.{U => _}, org.example2.declaration.data.{A, B, C}

  val y: Y = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_Hidden_SeparateImports_3 {
  import org.example2.declaration.{Y, Z}, org.example2.declaration.{U => _}, org.example2.declaration.data.{A, B, C}

  val y: Y = ???
  val z: Z = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_Hidden_SeparateImports_SomeOtherImportBetween_1 {
  import org.example2.declaration.{Y, Z}, org.example2.declaration.{U => _}, org.example2.declaration.beta.G, org.example2.declaration.data.{A, B, C}

  val y: Y = ???
  val z: Z = ???

  val g: G = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}
class Usage_Renamed_Hidden_SeparateImports_SomeOtherImportBetween_2 {
  import org.example2.declaration.data.{A, B, C}, org.example2.declaration.beta.G, org.example2.declaration.{Y, Z}, org.example2.declaration.{U => _}

  val y: Y = ???
  val z: Z = ???

  val g: G = ???

  val a: A = ???
  val b: B = ???
  val c: C = ???
}