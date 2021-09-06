package org.jetbrains.plugins.scala.uast

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.{ScalaLanguage, withPossibleSourceTypesCheck}
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.uast._
import org.jetbrains.uast.test.common.AllUastTypesKt.allUElementSubtypes
import org.jetbrains.uast.test.common.PossibleSourceTypesTestBase
import org.junit.runner.RunWith
import org.junit.runners.AllTests

import scala.jdk.CollectionConverters.IterableHasAsScala

@RunWith(classOf[AllTests])
class ScalaPossibleSourceTypesTest extends ScalaFileSetTestCase("/parser/data") with PossibleSourceTypesTestBase {
  override protected def getLanguage: Language = ScalaLanguage.INSTANCE

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

  override def checkConsistencyWithRequiredTypes(psiFile: PsiFile, classes: Class[_ <: UElement]*): Unit =
    PossibleSourceTypesTestBase.DefaultImpls.checkConsistencyWithRequiredTypes(this, psiFile, classes: _*)
}

object ScalaPossibleSourceTypesTest {
  def suite = new ScalaPossibleSourceTypesTest()
}
