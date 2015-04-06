package org.jetbrains.plugins.scala.lang.macros

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.impl.ModuleLibraryTable
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.{IdeaTestFixtureFactory, IdeaProjectTestFixture, TestFixtureBuilder, CodeInsightTestFixture}
import org.jetbrains.plugins.scala.LightScalaTestCase
import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, SimpleTestCase, ScalaLightCodeInsightFixtureTestAdapter}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScClass}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success}
import org.jetbrains.plugins.scala.util.TestUtils

class MonocleLensesTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  override def setUp() {
    super.setUp()
    addIvyCacheLibrary("monocle-core","com.github.julien-truffaut/monocle-core_2.11/jars", "monocle-core_2.11-1.2.0-SNAPSHOT.jar")
    addIvyCacheLibrary("monocle-macro","com.github.julien-truffaut/monocle-macro_2.11/jars", "monocle-macro_2.11-1.2.0-SNAPSHOT.jar")
    addIvyCacheLibrary("monocle-generic", "com.github.julien-truffaut/monocle-generic_2.11/jars", "monocle-generic_2.11-1.2.0-SNAPSHOT.jar")
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }

  protected def folderPath: String = TestUtils.getTestDataPath

  protected def addIvyCacheLibrary(libraryName: String, libraryPath: String, jarNames: String*) {
    val libsPath = TestUtils.getIvyCachePath
    val pathExtended = s"$libsPath/$libraryPath/"
    VfsRootAccess.allowRootAccess(pathExtended)
    PsiTestUtil.addLibrary(ModuleManager.getInstance(getProjectAdapter).getModules.head, libraryName, pathExtended, jarNames: _*)
  }


  def doTest(text: String, methodName: String, expectedType: String) = {
    val caretPos = text.indexOf("<caret>")
    configureFromFileTextAdapter("dummy.scala", text.replace("<caret>", ""))
    val exp = PsiTreeUtil.findElementOfClassAtOffset(getFileAdapter, caretPos, classOf[ScalaPsiElement], false).asInstanceOf[ScObject]
    exp.allMethods.find(_.name == methodName) match {
      case Some(x) => x.method.asInstanceOf[ScFunctionDefinition].returnType match {
        case Success(t, _) => assert(t.toString == expectedType, s"${t.toString} != $expectedType")
        case Failure(cause, _) => assert(false, cause)
      }
      case None => assert(false, "method not found")
    }
  }

  val lensesSimple =
    """
      |import monocle.macros.Lenses
      |
      |object Main {
      |  @Lenses
      |  case class Person(name: String, age: Int, address: Address)
      |  @Lenses
      |  case class Address(streetNumber: Int, streetName: String)
      |
      |  object <caret>Person {
      |    import Main.Address._
      |    val john = Person("John", 23, Address(10, "High Street"))
      |    age.get(john)
      |  }
      |}
    """.stripMargin

  val lensesTypeParams =
  """
    |import monocle.macros.Lenses
    |import monocle.syntax._
    |
    |object Main {
    |
    |  @Lenses
    |  case class Foo[A,B](q: Map[(A,B),Double], default: Double)
    |  object <caret>Foo {}
    |}
  """.stripMargin


  def testSimple()   = doTest(lensesSimple, "age", "monocle.Lens[Main.Person, Int]")
  def testTypeArgs() = doTest(lensesTypeParams, "q","monocle.Lens[Main.Foo[A, B], Map[(A, B), Double]]")
}
