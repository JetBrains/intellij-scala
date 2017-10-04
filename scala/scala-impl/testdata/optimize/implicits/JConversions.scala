import collection.JavaConversions
import java.util.ArrayList

object JConversions {
  def foo1 {
    import JavaConversions._
    val a: ArrayList[Int] = new ArrayList[Int]
    a.foreach(p => p)
  }

  def foo2 {
    import JavaConversions._
    val a: ArrayList[Int] = new ArrayList[Int]
    for (z <- a) {}
  }
}
/*
import java.util.ArrayList

import scala.collection.JavaConversions

object JConversions {
  def foo1 {
    import JavaConversions._
    val a: ArrayList[Int] = new ArrayList[Int]
    a.foreach(p => p)
  }

  def foo2 {
    import JavaConversions._
    val a: ArrayList[Int] = new ArrayList[Int]
    for (z <- a) {}
  }
}
*/