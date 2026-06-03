package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.{Message, ScalaHighlightingTestLike}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.util.{PsiFileTestUtil, TestUtils}
import org.jetbrains.plugins.scala.{ScalaFileType, TypecheckerTests}
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.Path

@Category(Array(classOf[TypecheckerTests]))
abstract class TypeInferenceTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with TypeInferenceDoTest
    with ScalaHighlightingTestLike {

  protected def folderPath: Path = Path.of(TestUtils.getTestDataPath, "typeInference")

  override protected val START = START_MARKER
  override protected val END = END_MARKER

  protected def doInjectorTest(injector: SyntheticMembersInjector): Unit = {
    val extensionArea = ApplicationManager.getApplication.getExtensionArea
    val extensionPoint = extensionArea.getExtensionPoint(SyntheticMembersInjector.EP_NAME)
    extensionPoint.registerExtension(injector, getTestRootDisposable)
    doTest()
  }

  override def configureFromFileText(fileName: String, fileTextOpt: Option[String]): ScalaFile = {
    val fileText = fileTextOpt.getOrElse {
      val nioFile = folderPath / fileName
      nioFile.readAllBytesToString(StandardCharsets.UTF_8)
    }
    val fileTextFinal = fileText.trim.withNormalizedSeparator
    configureFromFileText(ScalaFileType.INSTANCE, fileTextFinal)
    getFile.asInstanceOf[ScalaFile]
  }

  override protected def errorsFromAnnotator(file: PsiFile): Seq[Message.Error] =
    super.errorsFromScalaCode(file)

  protected def addFileToProject(fileName: String, text: String): PsiFile =
    PsiFileTestUtil.addFileToProject(fileName, text, getProject)

  protected def doTest(): Unit = {
    val fileName = getTestName(false) + ".scala"
    doTest(None, fileName = fileName)
  }
}
