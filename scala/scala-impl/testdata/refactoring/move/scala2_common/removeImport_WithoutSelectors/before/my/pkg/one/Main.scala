package my.pkg.one

import java.io.File
import java.net.URL

import my.pkg.two.OtherThing

object Main extends App {
  val t1 = Thing(new URL("https://nowhere.io"))
  val t2 = OtherThing(new File("/tmp/foo"))
  println(t1, t2)
}