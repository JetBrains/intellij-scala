package scala.meta.quasiquotes

import org.jetbrains.plugins.scala.base.libraryLoaders.ThirdPartyLibraryLoader
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import scala.meta.ScalaMetaLibrariesOwner

abstract class QuasiQuoteTypeInferenceTestBase extends TypeInferenceTestBase with ScalaMetaLibrariesOwner {

  override protected def additionalLibraries(): Array[ThirdPartyLibraryLoader] =
    super.additionalLibraries()

  override protected def doTest(fileText: String): Unit =
    super.doTest(
      s"""import scala.meta._
         |$fileText""".stripMargin)

}
