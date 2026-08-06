package test

import java.util.ArrayList

class JavaArrayTypeParameterInference {
  def arrayAAA() = {
    val list = new ArrayList[AAArrayType]()
    list.toArray(AAArrayType.emptyArray())
  }

  arrayAAA().foreach(_.<ref>foo)
}