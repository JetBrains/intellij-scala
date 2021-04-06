package org.jetbrains.plugins.scala

package object traceLogger {
  type Data = String
  type ValueDesc = (String, Data)
  type StackTrace = Array[StackTraceElement]
}
