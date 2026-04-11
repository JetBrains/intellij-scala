package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgument, ScTypeElement}
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.{ScopeItem, ScopeSuggester}
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.junit.Assert

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.annotation.nowarn

abstract class AbstractScopeSuggesterTest extends ScalaLightCodeInsightFixtureTestCase {
  val BEGIN_MARKER: String = "/*begin*/"
  val END_MARKER: String = "/*end*/"

  protected def folderPath: Path = refactoringCommonTestDataRoot / "introduceVariable" / "scopeSuggester"

  protected def doTest(suggestedScopesNames: Seq[String]): Unit = {
    val fileName = getTestName(false) + ".scala"
    val filePath = folderPath / fileName
    assert(filePath.exists, "file " + filePath + " not found")

    val fileText = filePath.readAllBytesToString(StandardCharsets.UTF_8).withNormalizedSeparator
    configureFromFileText(fileName, fileText)

    val startOffset = fileText.indexOf(BEGIN_MARKER) + BEGIN_MARKER.length
    val endOffset = fileText.indexOf(END_MARKER)

    assert(startOffset != -1, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")

    @nowarn("cat=deprecation")
    val editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContextFromFocus.getResult)

    editor.getSelectionModel.setSelection(startOffset, endOffset)

    val scalaFile = getFile.asInstanceOf[ScalaFile]
    @nowarn("cat=deprecation")
    var element = CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContextFromFocus.getResult)
    if (element == null) {
      element = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset, endOffset, classOf[PsiElement])
    }

    val typeElement = element match {
      case te: ScTypeElement => Option(te)
      case ta: ScTypeArgument => ta.typeElement
      case _ => None
    }

    assert(typeElement.isDefined, "Selected element should be ScTypeElement or ScTypeArgument")

    val scopes: Array[ScopeItem] = ScopeSuggester.suggestScopes(typeElement.get)
    Assert.assertEquals(scopes.map(_.name).sorted.mkString(", "), suggestedScopesNames.sorted.mkString(", "))
  }
}
