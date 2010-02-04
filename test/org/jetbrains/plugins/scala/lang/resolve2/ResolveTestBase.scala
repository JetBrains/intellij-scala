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

/**
 * Pavel.Fatin, 02.02.2010
 */

abstract class ResolveTestBase() extends ScalaResolveTestCase {
  val pattern = """/\*\s*(.*?)\s*\*/\s*""".r
  type Parameters = Map[String, String]

  val RESOLVED = "resolved"
  val NAME = "name"
  val FILE = "file"
  val LINE = "line"
  val OFFSET = "offset"
  val LENGTH = "length"
  val TYPE = "type"
  val PATH = "path"

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
        case RESOLVED | NAME | FILE | LINE | OFFSET | LENGTH | TYPE | PATH =>
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

    val target = reference.resolve

    if (options.contains(RESOLVED) && options(RESOLVED) == "false") {
      Assert.assertNull("Reference must not be resolved: " + referenceName + ":\n" + myFile.getText, target);
    } else {
      Assert.assertNotNull("Reference must be resolved: " + referenceName + ":\n" + myFile.getText, target);

      if (options.contains(PATH)) {
        Assert.assertEquals(PATH, options(PATH), target.asInstanceOf[ScTypeDefinition].getQualifiedName)
      }

      if (options.contains(FILE)) {
        val targetFile = target.getContainingFile.getVirtualFile.getNameWithoutExtension
        Assert.assertEquals(FILE, options(FILE), targetFile)
      }

      val expectedName = if (options.contains(NAME)) options(NAME) else referenceName
      Assert.assertEquals(NAME, expectedName, target.asInstanceOf[PsiNamedElement].getName)
      
      if (options.contains(LINE)) {
        Assert.assertEquals(LINE, options(LINE).toInt, getLine(target))
      }

      if (options.contains(OFFSET)) {
        Assert.assertEquals(OFFSET, options(OFFSET).toInt, target.getTextOffset)
      }

      if (options.contains(LENGTH)) {
        Assert.assertEquals(LENGTH, options(LENGTH).toInt, target.getTextLength)
      }

      if (options.contains(TYPE)) {
        val expectedClass = Class.forName(options(TYPE))
        val targetClass = target.getClass
        Assert.assertTrue(TYPE + ": expected " + expectedClass.getSimpleName + ", but was " + targetClass.getSimpleName,
          expectedClass.isAssignableFrom(targetClass))
      }
    }
  }

  def getLine(element: PsiElement) = {
    element.getContainingFile.getText.substring(0, element.getTextOffset).count(_ == '\n') + 1
  }
}

