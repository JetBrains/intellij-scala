package org.jetbrains.plugins.scala.uast

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.uast.test.common.AllUastTypesKt.allUElementSubtypes
import org.jetbrains.uast.test.common.PossibleSourceTypesTestBase
import org.jetbrains.uast._
import org.junit.runner.RunWith
import org.junit.runners.AllTests

import scala.jdk.CollectionConverters.IterableHasAsScala

@RunWith(classOf[AllTests])
class ScalaPossibleSourceTypesTest extends ScalaFileSetTestCase("/parser/scala3Import/success") with PossibleSourceTypesTestBase {
  override protected def runTest(testName0: String, content: String, project: Project): Unit = {
    val file = createLightFile(content, project)
    val uFile = UastFacade.INSTANCE.convertElement(file, null, null).asInstanceOf[UFile]

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
