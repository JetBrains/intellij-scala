package org.jetbrains.plugins.scala.lang.formatting.patterns

package indent {

  /**
  *  For blocks & composite expressions
  */
  trait BlockedIndent

  /**
  *  For template declarations and definitions
  */
  trait TemplateIndent

  /**
  *  For parameter lists
  */
  trait ContiniousIndent

  /**
  *  For if else statement
  */
  trait IfElseIndent

}