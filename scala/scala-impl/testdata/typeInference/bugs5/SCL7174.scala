package controllers

import scala.collection.mutable._

import controllers.core.Foo

package core {
class Foo {
  def foo = 123
}
}


class Test extends Foo {
  val x: ArrayBuffer[Int] = new ArrayBuffer[Int]()

  /*start*/foo/*end*/
}
//Int