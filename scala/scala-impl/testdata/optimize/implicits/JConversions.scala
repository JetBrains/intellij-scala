// Notification message: Removed 1 import, added 1 import
import collection.JavaConverters
import java.util.ArrayList

object JConversions {
  def foo1 {
    import JavaConverters._
    val a: ArrayList[Int] = new ArrayList[Int]
    a.asScala.foreach(p => p)
  }

  def foo2 {
    import JavaConverters._
    val a: ArrayList[Int] = new ArrayList[Int]
    for (z <- a.asScala) {}
  }
}
/*
import java.util.ArrayList
import scala.collection.JavaConverters

object JConversions {
  def foo1 {
    import JavaConverters._
    val a: ArrayList[Int] = new ArrayList[Int]
    a.asScala.foreach(p => p)
  }

  def foo2 {
    import JavaConverters._
    val a: ArrayList[Int] = new ArrayList[Int]
    for (z <- a.asScala) {}
  }
}
*/