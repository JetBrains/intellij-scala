package org.jetbrains.plugins.scala.uast

import com.intellij.platform.uast.testFramework.common.AllUastTypesKt.allUElementSubtypes
import com.intellij.platform.uast.testFramework.common.PossibleSourceTypesTestBase
import org.jetbrains.plugins.scala.base.{DefaultFileSetTestTransform, NoSdkFileSetTestBase}
import org.jetbrains.plugins.scala.lang.psi.uast.withPossibleSourceTypesCheck
import org.jetbrains.uast.{UCallableReferenceExpression, UClass, UField, UFile, UMethod, UQualifiedReferenceExpression, USimpleNameReferenceExpression, UastFacade}

import java.nio.file.Path
import scala.jdk.CollectionConverters._

class ScalaPossibleSourceTypesTest extends NoSdkFileSetTestBase with DefaultFileSetTestTransform with PossibleSourceTypesTestBase {
  override protected def relativeTestDataPath: Path = Path.of("parser", "data")

  override protected def runTest(testName: String, fileText: String): Unit = withPossibleSourceTypesCheck {
    val file = createLightFile(fileText)
    val uFile = UastFacade.INSTANCE.convertElementWithParent[UFile](file, Array.empty)

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
