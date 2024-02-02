package pack1.pack4

import pack1.pack2.pack3.{TopLevelDef, TopLevelVal, topLevelVar}

def useToplevelDefs(): Unit = {
  TopLevelDef()
  println(TopLevelVal)
  println(topLevelVar)
}
