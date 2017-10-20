package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

object PrecedenceTypes {
  val PREFIX_COMPLETION = 0
  val JAVA_LANG = 1
  val SCALA = 2
  val SCALA_PREDEF = 3
  val PACKAGE_LOCAL_PACKAGE = 4
  val WILDCARD_IMPORT_PACKAGE = 5
  val IMPORT_PACKAGE = 6
  val PACKAGE_LOCAL = 7
  val WILDCARD_IMPORT = 8
  val IMPORT = 9
  val OTHER_MEMBERS = 10
}
