package org.example2.usage

import org.example2.declaration.data.U
import org.example2.declaration.{X, Y}

class Usage_OfGlobal {
  val u: U = ???
  val x: X = ???
  val y: Y = ???
}

//
// SIMPLE
//
class Usage_Simple_1 {

  import org.example2.declaration.data.U

  val u: U = ???
}
class Usage_Simple_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.data.U

  val u: U = ???
  val y: Y = ???
}
class Usage_Simple_3 {

  import org.example2.declaration.data.U
  import org.example2.declaration.{Y, Z}

  val u: U = ???
  val y: Y = ???
  val z: Z = ???
}

//
// SIMPLE SEPARATE IMPORTS
//
class Usage_Simple_SeparateImports_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.data.U

  val u: U = ???
  val y: Y = ???
}

class Usage_Simple_SeparateImports_3 {

  import org.example2.declaration.data.U
  import org.example2.declaration.{Y, Z}

  val u: U = ???
  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED
//
class Usage_Renamed_1 {

  import org.example2.declaration.data.{U => U_Renamed1}

  val u: U_Renamed1 = ???
}
class Usage_Renamed_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.data.{U => U_Renamed2}

  val u: U_Renamed2 = ???
  val y: Y = ???
}
class Usage_Renamed_3 {

  import org.example2.declaration.data.{U => U_Renamed3}
  import org.example2.declaration.{Y, Z}

  val u: U_Renamed3 = ???
  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED HIDDEN
//
class Usage_Renamed_Hidden_1 {

  import org.example2.declaration.data.{U => _}

}
class Usage_Renamed_Hidden_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.data.{U => _}

  val y: Y = ???
}
class Usage_Renamed_Hidden_3 {

  import org.example2.declaration.data.{U => _}
  import org.example2.declaration.{Y, Z}

  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED SEPARATE IMPORTS
//
class Usage_Renamed_SeparateImports_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.data.{U => U_Renamed2}

  val u: U_Renamed2 = ???
  val y: Y = ???
}
class Usage_Renamed_SeparateImports_3 {

  import org.example2.declaration.data.{U => U_Renamed3}
  import org.example2.declaration.{Y, Z}

  val u: U_Renamed3 = ???
  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED HIDDEN SEPARATE IMPORTS
//
class Usage_Renamed_Hidden_SeparateImports_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.data.{U => _}

  val y: Y = ???
}
class Usage_Renamed_Hidden_SeparateImports_3 {

  import org.example2.declaration.data.{U => _}
  import org.example2.declaration.{Y, Z}

  val y: Y = ???
  val z: Z = ???
}