package pack1.pack4

import pack3.pack2.{PackObjectDef, PackObjectVal}

object Usage {
  def usePackageDefs(): Unit = {
    PackObjectDef()
    println(PackObjectVal)
  }
}
