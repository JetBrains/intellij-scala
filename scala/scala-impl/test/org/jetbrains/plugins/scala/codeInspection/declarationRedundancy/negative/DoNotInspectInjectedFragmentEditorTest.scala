package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.source.resolve.FileContextUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaFileNameInspection
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{ScalaAccessCanBeTightenedInspection, ScalaUnusedDeclarationInspection}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.junit.Assert.assertTrue

import scala.jdk.CollectionConverters.CollectionHasAsScala

class DoNotInspectInjectedFragmentEditorTest extends ScalaLightCodeInsightFixtureTestCase {
  def test_unused_declarations(): Unit = {
    myFixture.enableInspections(classOf[ScalaUnusedDeclarationInspection])
    doCommonTest("class DoNotInspectInjectedFragmentEditorTest")
  }

  def test_can_be_private(): Unit = {
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
    doCommonTest("class DoNotInspectInjectedFragmentEditorTest { val doNotInspectMe = 42; println(doNotInspectMe) }")
  }

  def test_scala_file_name_inspection(): Unit = {
    myFixture.enableInspections(classOf[ScalaFileNameInspection])
    doCommonTest("class DoNotInspectInjectedFragmentEditorTest")
  }

  private def doCommonTest(injectedCode: String): Unit = {
    val file = myFixture.configureByText("Foo.scala", injectedCode)
    val mockedInjectedCodeContext = ScalaCodeFragment.create("\"\"", ScalaLanguage.INSTANCE)(getProject)
    val mockedLiteral = mockedInjectedCodeContext.findElementAt(0).getContext
    file.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, SmartPointerManager.createPointer(mockedLiteral))
    val highlights = myFixture.doHighlighting().asScala.filterNot(_.getDescription == null)
    assertTrue(highlights.isEmpty)
  }
}
