package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

abstract class ScalaAccessCanBePrivateInspectionTestBase extends ScalaInspectionTestBase {

  override protected val description : String = ScalaInspectionBundle.message("access.can.be.private")

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaAccessCanBeTightenedInspection]

  override def setUp(): Unit = {
    super.setUp()

    deleteAllGlobalScratchFiles(getProject)
  }
}
