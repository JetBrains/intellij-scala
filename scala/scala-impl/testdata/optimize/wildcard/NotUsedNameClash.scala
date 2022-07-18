// Notification message: Removed 5 imports, added 1 import
import java.util.{Date, Timer, Enumeration, UUID, Locale}
import scala.util._

class NotUsedNameClash { //Both packages have `Random` class
  def foo(): Unit = {
    val d: Date = null
    val t: Timer = null
    val e: Enumeration[String] = null
    val uuid: UUID = null
    val l: Locale = null
    val tr: Try = null
  }
}

/*
import java.util._
import scala.util._

class NotUsedNameClash { //Both packages have `Random` class
  def foo(): Unit = {
    val d: Date = null
    val t: Timer = null
    val e: Enumeration[String] = null
    val uuid: UUID = null
    val l: Locale = null
    val tr: Try = null
  }
}
*/