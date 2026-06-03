package pack1.pack4

import pack0.pack1.pack2.{TopLevelClass, TopLevelDef, TopLevelVal, topLevelVar}

def useToplevelDefs(): Unit = {
  val tlc = TopLevelClass()
  TopLevelDef()
  println(TopLevelVal)
  println(topLevelVar)
}
