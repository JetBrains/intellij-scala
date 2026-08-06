package pack1.pack3

import pack1.pack2.{PackObjectDef, PackObjectVal}

object Usage {
  def usePackageDefs(): Unit = {
    PackObjectDef()
    println(PackObjectVal)
  }
}
