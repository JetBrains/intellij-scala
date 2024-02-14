package org.jetbrains.plugins.scala.uast

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.platform.uast.testFramework.common.AllUastTypesKt.allUElementSubtypes
import com.intellij.platform.uast.testFramework.common.PossibleSourceTypesTestBase
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.lang.parser.ScalaFileSetParserTestCase
import org.jetbrains.plugins.scala.lang.psi.uast.withPossibleSourceTypesCheck
import org.jetbrains.plugins.scala.{ScalaLanguage, ScalaVersion}
import org.jetbrains.uast._

import scala.jdk.CollectionConverters.IterableHasAsScala

class ScalaPossibleSourceTypesTest extends TestCase

object ScalaPossibleSourceTypesTest {
  def suite(): Test = new ScalaFileSetParserTestCase("/parser/data") with PossibleSourceTypesTestBase {
    override protected def getLanguage: Language = ScalaLanguage.INSTANCE

    override protected def supportedInScalaVersion(version: ScalaVersion): Boolean =
      version == ScalaVersion.Latest.Scala_2_13

    override protected def runTest(testName0: String, content: String, project: Project): Unit = withPossibleSourceTypesCheck {
      val file = createLightFile(content, project)
      val uFile = UastFacade.INSTANCE.convertElementWithParent[UFile](file, Array())

      val psiFile = uFile.getSourcePsi
      for (uastType <- allUElementSubtypes.asScala) {
        checkConsistencyWithRequiredTypes(psiFile, uastType)
      }
      checkConsistencyWithRequiredTypes(psiFile, classOf[UClass], classOf[UMethod], classOf[UField])
      checkConsistencyWithRequiredTypes(
        psiFile,
        classOf[USimpleNameReferenceExpression],
        classOf[UQualifiedReferenceExpression],
        classOf[UCallableReferenceExpression]
      )
    }
  }
}
