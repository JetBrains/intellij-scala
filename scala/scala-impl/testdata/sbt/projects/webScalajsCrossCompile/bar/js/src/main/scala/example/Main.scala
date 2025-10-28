package example

import org.scalajs.dom.console

object Main {
  def main(args: Array[String]): Unit = {
    val lib = new MyLibrary
    console.log(lib.sq(2))

    console.log(s"Using Scala.js version ${System.getProperty("java.vm.version")}")
  }
}
