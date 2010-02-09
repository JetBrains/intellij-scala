package org.jetbrains.plugins.scala.lang.resolve2

import com.intellij.testFramework.{ResolveTestCase, PsiTestCase}
import com.intellij.openapi.application.ex.PathManagerEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.openapi.vfs.VirtualFile
import scala.util.matching.Regex.{Match, MatchData}
import junit.framework._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScClass}
import com.intellij.psi.{PsiNamedElement, PsiReference, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import java.lang.String

/**
 * Pavel.Fatin, 02.02.2010
 */

abstract class ResolveTestBase() extends ScalaResolveTestCase {
  val pattern = """/\*\s*(.*?)\s*\*/\s*""".r
  type Parameters = Map[String, String]

  val Resolved = "resolved"
  val Name = "name"
  val File = "file"
  val Line = "line"
  val Offset = "offset"
  val Length = "length"
  val Type = "type"
  val Path = "path"
  val Applicable = "applicable"

  var options: List[Parameters] = List()
  var references: List[PsiReference] = List()


  override def setUp() {
    super.setUp
    options = List()
    references = List()
  }

  override def getTestDataPath: String = {
    return TestUtils.getTestDataPath + "/resolve2/"
  }

  override def configureByFileText(text: String, fileName: String, parentDir: VirtualFile): PsiReference = {
    myFile = if (parentDir == null) createFile(myModule, fileName, text) else createFile(myModule, parentDir, fileName, text)

    val matches = pattern.findAllIn(text).matchData

    for (m <- matches) {
      val each = parseParameters(m.group(1))
      check(each)
      options = each :: options
      references = myFile.findReferenceAt(m.end) :: references
    }

    Assert.assertFalse("At least one reference must be specified", references.isEmpty)
    Assert.assertEquals("Options number", references.size, options.size)

    null
  }

  def check(parameters: Parameters) = {
    for ((key, value) <- parameters) {
      key match {
        case Resolved | Name | File | Line | Offset | Length | Type | Path | Applicable =>
        case _ => Assert.fail("Unknown parameter: " + key)
      }
    }
  }

  def parseParameters(s: String): Parameters = {
    if (s.isEmpty) Map() else Map(s.split("""\s*,\s*""").map(_.trim).map {
      it: String => val parts = it.split("""\s*:\s*"""); (parts(0), parts(1))
    }: _*)
  }

  def doTest {
    doTest(getTestName(false) + ".scala")
  }

  def doTest(file: String) {
    configureByFile(file)
    references.zip(options).foreach(it => doEachTest(it._1, it._2))
  }

  def doEachTest(reference: PsiReference, options: Parameters) {
    val referenceName = reference.getElement.getText

    val (target, applicable) = reference.asInstanceOf[ScReferenceElement].advancedResolve

    val description: String = referenceName + ":\n\n" + myFile.getText + "\n"

    if (options.contains(Resolved) && options(Resolved) == "false") {
      Assert.assertNull("Reference must NOT be resolved: " + description, target);
    } else {
      Assert.assertNotNull("Reference must BE resolved: " + description, target);

      if (options.contains(Applicable) && options(Applicable) == "false") {
        Assert.assertFalse("Reference must NOT be applicable: " + description, applicable);
      } else {
        Assert.assertTrue("Reference must BE applicable: " + description, applicable);
      }

      if (options.contains(Path)) {
        Assert.assertEquals(Path, options(Path), target.asInstanceOf[ScTypeDefinition].getQualifiedName)
      }

      if (options.contains(File) || options.contains(Offset)) {
        val actual = target.getContainingFile.getVirtualFile.getNameWithoutExtension
        val expected = if (!options.contains(File) || options(File) == "this") {
          reference.getElement.getContainingFile.getVirtualFile.getNameWithoutExtension
        } else options(File)
        Assert.assertEquals(File, expected, actual)
      }

      val expectedName = if (options.contains(Name)) options(Name) else referenceName
      Assert.assertEquals(Name, expectedName, target.asInstanceOf[PsiNamedElement].getName)

      if (options.contains(Line)) {
        Assert.assertEquals(Line, options(Line).toInt, getLine(target))
      }

      if (options.contains(Offset)) {
        Assert.assertEquals(Offset, options(Offset).toInt, target.getTextOffset)
      }

      if (options.contains(Length)) {
        Assert.assertEquals(Length, options(Length).toInt, target.getTextLength)
      }

      if (options.contains(Type)) {
        val expectedClass = Class.forName(options(Type))
        val targetClass = target.getClass
        Assert.assertTrue(Type + ": expected " + expectedClass.getSimpleName + ", but was " + targetClass.getSimpleName,
          expectedClass.isAssignableFrom(targetClass))
      }
    }
  }

  def getLine(element: PsiElement) = {
    element.getContainingFile.getText.substring(0, element.getTextOffset).count(_ == '\n') + 1
  }
}

