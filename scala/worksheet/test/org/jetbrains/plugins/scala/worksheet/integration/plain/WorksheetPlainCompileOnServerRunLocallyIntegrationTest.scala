package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.WorksheetEvaluationTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[WorksheetEvaluationTests]))
class WorksheetPlainCompileOnServerRunLocallyIntegrationTest extends WorksheetPlainIntegrationBaseTest {

  override def useCompileServer = true

  override def runInCompileServerProcess = false
}
