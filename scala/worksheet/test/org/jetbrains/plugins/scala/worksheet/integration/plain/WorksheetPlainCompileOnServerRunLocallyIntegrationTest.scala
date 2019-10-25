package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest extends WorksheetPlainIntegrationBaseTest {

  override def compileInCompileServerProcess = true

  override def runInCompileServerProcess = false
}
