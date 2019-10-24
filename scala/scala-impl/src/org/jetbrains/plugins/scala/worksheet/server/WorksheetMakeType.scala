package org.jetbrains.plugins.scala.worksheet.server

abstract sealed class WorksheetMakeType

object InProcessServer extends WorksheetMakeType // compile server is enabled, worksheet is compiled in server and run inside the server process
object OutOfProcessServer extends WorksheetMakeType // compile server is enabled, worksheet is compiled in server is run outside the server process
object NonServer extends WorksheetMakeType // compile server is disabled

