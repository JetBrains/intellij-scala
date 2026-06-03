package org.jetbrains.plugins.scala.lang.actions.editor.enter

import java.nio.file.Path

class AddUnitFunctionSignatureTest extends AbstractEnterActionTestBase {
  override protected def relativeTestDataPath: Path = Path.of("actions", "editor", "enter", "addunit")

  override protected def setUp(): Unit = {
    super.setUp()

    scalaCodeStyleSettings.TYPE_ANNOTATION_UNIT_TYPE = true
    scalaCodeStyleSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true
  }
}
