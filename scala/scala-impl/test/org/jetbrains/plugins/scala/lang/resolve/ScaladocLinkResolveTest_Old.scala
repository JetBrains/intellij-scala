package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.base.SdkConfiguration
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.{ScParameterImpl, ScTypeParamImpl}
import org.junit.Assert.{assertEquals, assertTrue}

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class ScaladocLinkResolveTestBase_Old extends ScalaResolveTestCase {
  protected def genericResolve(expectedLength: Int, expectedClass: Class[_]): Unit = try {
    //NOTE: the file is prepared in `setUp`
    val caretOffsets: Seq[Int] = getEditor.getCaretModel.getAllCarets.asScala.map(_.getOffset).toSeq
    for (caretOffset <- caretOffsets) {
      val reference = getFile.findReferenceAt(caretOffset)
      val results = reference.asInstanceOf[ScReference].multiResolveScala(false)
      assertEquals(
        s"""Wrong number of resolved elements for reference: $reference
           |All elements:
           |${results.mkString("\n")}
           |""".stripMargin.trim,
        expectedLength,
        results.length
      )

      if (expectedLength != 0) {
        val resolved = results(0).getElement
        assertTrue(expectedClass.isAssignableFrom(resolved.getClass))
        val refText = reference.asInstanceOf[ScReference].getText
        if (expectedClass eq classOf[PsiClass]) {
          val resolved1 = resolved.asInstanceOf[PsiClass]
          var qualifiedName = resolved1.getQualifiedName
          val name = resolved1.getName
          resolved match {
            case definition: ScTemplateDefinition =>
              qualifiedName = definition.qualifiedName
            case _ =>
          }
          assertTrue(qualifiedName == refText || name == refText)
        }
        else assertTrue(resolved.asInstanceOf[ScNamedElement].getName == refText)
      }
    }
  } catch {
    case throwable: Throwable =>
      System.err.println(s"Test file: $testFilePath")
      throw throwable
  }
}

/** Also see [[org.jetbrains.plugins.scala.lang.resolve2.ScalaDocLinkResolveTest]] */
class ScaladocLinkResolveTest_Old extends ScaladocLinkResolveTestBase_Old {

  override def folderPath: String = super.folderPath + "resolve/scaladoc"
  def testCodeLinkMultiResolve(): Unit = genericResolve(2, classOf[PsiClass])
  def testMethodParamResolve(): Unit = genericResolve(1, classOf[ScParameterImpl])
  def testMethodTypeParamResolve(): Unit = genericResolve(1, classOf[ScTypeParamImpl])
  def testMethodParamNoResolve(): Unit = genericResolve(0, classOf[PsiClass])
  def testPrimaryConstrParamResolve(): Unit = genericResolve(1, classOf[ScParameterImpl])
  def testPrimaryConstrTypeParamResolve(): Unit = genericResolve(1, classOf[ScTypeParamImpl])
  def testPrimaryConstrParamNoResolve(): Unit = genericResolve(0, classOf[PsiClass])
  def testPrimaryConstrTypeParamNoResolve(): Unit = genericResolve(0, classOf[PsiClass])
  def testTypeAliasParamResolve(): Unit = genericResolve(1, classOf[ScTypeParamImpl])
  def testTypeAliasParamNoResolve(): Unit = genericResolve(0, classOf[PsiClass])
}

class ScaladocLinkResolveJavaDesktopClassesTest_Old extends ScaladocLinkResolveTestBase_Old {
  override def folderPath: String = super.folderPath + "resolve/scaladoc"

  override protected def sdkConfiguration: SdkConfiguration = SdkConfiguration.IncludedModules(Seq("java.base", "java.desktop"))

  def testCodeLinkResolve(): Unit = genericResolve(1, classOf[PsiClass])
}
