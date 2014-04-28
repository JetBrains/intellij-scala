package org.jetbrains.plugins.scala
package worksheet.server

/**
 * User: Dmitry Naydanov
 * Date: 2/11/14
 */
abstract sealed class WorksheetMakeType

object InProcessServer extends WorksheetMakeType
object OutOfProcessServer extends WorksheetMakeType
object NonServer extends WorksheetMakeType

