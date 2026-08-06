// Notification message: Rearranged imports
package com.jetbrains
package test

import java.util.Date
import java.sql.Connection

abstract class SortedInPackage {
  import java.sql.Clob
  import java.sql.Blob

  val d: Date
  val s: Connection
  val c: Clob
  val b: Blob
}
/*
package com.jetbrains
package test

import java.sql.Connection
import java.util.Date

abstract class SortedInPackage {
  import java.sql.{Blob, Clob}

  val d: Date
  val s: Connection
  val c: Clob
  val b: Blob
}
*/