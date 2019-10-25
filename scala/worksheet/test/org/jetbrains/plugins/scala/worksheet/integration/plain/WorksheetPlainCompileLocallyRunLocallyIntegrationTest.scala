package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class WorksheetPlainCompileLocallyRunLocallyIntegrationTest extends WorksheetPlainIntegrationBaseTest {

  override def compileInCompileServerProcess = false

  // the value doesn't actually matter, cause compile server isn't used anyway
  override def runInCompileServerProcess = false
}
