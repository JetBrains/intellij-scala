package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class Scala3UnusedGlobalDeclarationInspectionTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  override protected def afterSetUpProject(project: Project, module: com.intellij.openapi.module.Module): Unit = {

    super.afterSetUpProject(project, module)

    // TODO Turn back on (by removing the Scala3GlobalDeclarationUsedFromJavaInspectionTest.afterSetUpProject
    //  implementation you are currently reading) when platform has implemented support for inspections which
    //  run references search.
    //  See https://jetbrains.slack.com/archives/CMDBCUBGE/p1682686264596449?thread_ts=1682534407.272869&cid=CMDBCUBGE
    //  ----
    //  I've only disabled the ast.loading.filter here and in a few other places, because as far as I know the filter
    //  is operating as desired and not causing issues anywhere else. The implementation where we enable it resides in
    //  ScalaLightCodeInsightFixtureTestCase, which is used for many tests.

    Registry.get("ast.loading.filter").setValue(false, getTestRootDisposable)
  }

  private def addFile(text: String): Unit = myFixture.addFileToProject("Foo.scala", text)

  def test_extension_method(): Unit = {
    addFile("object Bar { import Foo.* }")
    checkTextHasError(s"object Foo { extension(i: Int) { def ${START}plus0$END: Int = i + 0 } }")
  }

  def test_enum(): Unit = {
    addFile("object Bar { import Foo.* }")
    checkTextHasError(s"object Foo { enum ${START}Language$END { case ${START}Spanish$END } }")
  }

  def test_enum_case(): Unit = {
    addFile("object Bar { import Foo.Language.*; Spanish match { case Spanish => } }")
    checkTextHasError(s"object Foo { enum Language { case ${START}English$END, Spanish } }")
  }

  def test_parameterized_enum(): Unit = {
    addFile("object Bar { import Foo.* }")
    checkTextHasError(s"object Foo { enum ${START}Fruit$END(val i: Int = 42) { case ${START}Strawberry$END } }")
  }

  def test_parameterized_enum_case(): Unit = {
    addFile("object Bar { import Foo.Fruit.* }")
    checkTextHasError(s"object Foo { enum Fruit { case ${START}Strawberry$END(i: Int) } }")
  }
}
