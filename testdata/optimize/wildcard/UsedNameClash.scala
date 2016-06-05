import java.util.{Date, Timer, Enumeration, UUID, Random}
import scala.util._

class UsedNameClash {
  def foo(): Unit = {
    val d: Date = null
    val t: Timer = null
    val e: Enumeration[String] = null
    val uuid: UUID = null
    val r: Random = null //Both packages have `Random` class
    val tr: Try = null
  }
}

/*
import java.util.{Random => _, _}

import scala.util._

class UsedNameClash {
  def foo(): Unit = {
    val d: Date = null
    val t: Timer = null
    val e: Enumeration[String] = null
    val uuid: UUID = null
    val r: Random = null //Both packages have `Random` class
    val tr: Try = null
  }
}
*/