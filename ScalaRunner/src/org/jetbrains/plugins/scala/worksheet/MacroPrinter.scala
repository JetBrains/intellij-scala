package org.jetbrains.plugins.scala.worksheet

import scala.reflect.macros.Context
import scala.language.experimental.macros

/**
 * User: Dmitry Naydanov
 * Date: 1/21/14
 */
object MacroPrinter {
  def printDefInfo[T](toPrint: T) = macro printDefImpl[T]

  def printTypeInfo[T] = macro printTypeInfoImpl[T]

  /**
   * Usage 
   * {{{
   *   printImportInfo({import java.io.File;})
   * }}}
   */
  def printImportInfo[T](toPrint: T) = macro printImportInfoImpl[T]

  def printDefImpl[T: c.WeakTypeTag](c: Context)(toPrint: c.Expr[T]) = c literal toPrint.tree.tpe.toString

  def printTypeInfoImpl[T](c: Context)(implicit ev: c.WeakTypeTag[T]) = c literal ev.tpe.toString

  def printImportInfoImpl[T: c.WeakTypeTag](c: Context)(toPrint: c.Expr[T]) = {
    toPrint.tree match {
      case c.universe.Block(imp, _) => c literal imp.head.toString()
    }
  }
}

class MacroPrinter {}