import java.util.Date
import java.lang.reflect.Method
import java.sql.Connection

abstract class Sorted {
  val d: Date
  val s: Connection
  val m: Method
}
/*
import java.lang.reflect.Method
import java.sql.Connection
import java.util.Date

abstract class Sorted {
  val d: Date
  val s: Connection
  val m: Method
}
*/