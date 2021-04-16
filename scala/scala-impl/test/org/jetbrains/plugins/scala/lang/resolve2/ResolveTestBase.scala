package org.jetbrains.plugins.scala.lang.resolve2

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.junit.Assert._

/**
 * Pavel.Fatin, 02.02.2010
 */

abstract class ResolveTestBase extends ScalaResolveTestCase {
  val pattern = """/\*\s*(.*?)\s*\*/\s*""".r
  type Parameters = Map[String, String]

  val Resolved = "resolved" // default: true
  val Name = "name" // default: reference name
  val File = "file" // default: this (if line or offset provided)
  val Line = "line"
  val Offset = "offset"
  val Length = "length"
  val Type = "type"
  val Path = "path"
  val Applicable = "applicable" // default: true
  val Accessible = "accessible" // default: true

  val Parameters = List(Resolved, Name, File, Line, Offset, Length, Type, Path, Applicable, Accessible)

  var options: List[Parameters] = List()
  var references: List[PsiReference] = List()


  override def setUp(): Unit = {
    super.setUp()
    configureReferences()
  }

  override def folderPath: String = {
    super.folderPath + "resolve2/"
  }

  def configureReferences(): PsiReference = {
    options = List()
    references = List()

    val matches = pattern.findAllIn(getFile.getText).matchData

    for (m <- matches) {
      val parameters = parseParameters(m.group(1))
      val reference = getFile.findReferenceAt(m.end)

      assertKnown(parameters)
      assertNotNull("No reference found at offset " + m.end, references)

      options = parameters :: options
      references = reference :: references
    }

    options = options.reverse
    references = references.reverse
    
    assertFalse("At least one expectation must be specified", references.isEmpty)
    assertEquals("Options number", references.size, options.size)

    null
  }

  def assertKnown(parameters: Parameters): Unit = {
    for ((key, value) <- parameters) {
      assertTrue("Unknown parameter: " + key + "\nAllowed: " + Parameters.mkString(", "),
        Parameters.contains(key))
    }
  }

  def parseParameters(s: String): Parameters = {
    if (s.isEmpty) Map() else Map(s.split("""\s*,\s*""").map(_.trim).map {
      (it: String) =>
        val parts = it.split("""\s*:\s*""")
        (parts(0), parts(1))
    }.toSeq: _*)
  }

  def doTest(): Unit =
    doTestImpl()

  private def doTestImpl(): Unit =
    references.zip(options).foreach { it =>
      it._1 match {
        case ref: ScReference =>
          doEachTest(ref, it._2)
        case ref: PsiMultiReference =>
          val hostReferences = ref.getReferences
          if (hostReferences.length == 2) {
            hostReferences.find(_.isInstanceOf[ScReference]) match {
              case Some(r: ScReference) =>
                doEachTest(r, it._2)
              case _ =>
                assert(assertion = false, message = "Multihost references are not supported")
            }
          } else {
            assert(assertion = false, message = "Multihost references are not supported")
          }
      }
    }

  def doEachTest(reference: ScReference, options: Parameters): Unit = {
    val referenceName = reference.refName
    val result = reference.bind()
    val (target, accessible, applicable) = if(result.isDefined) (
            result.get.element,
            result.get.isAccessible,
            result.get.isApplicable()) else (null, true, true)

    def message = format(getFile.getText, _: String, lineOf(reference))

    def assertEquals(name: String, v1: Any, v2: Any): Unit = {
      if(v1 != v2) fail(message(name + " - expected: " + v1 + ", actual: " + v2))
    }

    if (options.contains(Resolved) && options(Resolved) == "false") {
      assertNull(message(referenceName + " must NOT be resolved!"), target)
    } else {
      assertNotNull(message(referenceName + " must BE resolved!"), target)

      if (options.contains(Accessible) && options(Accessible) == "false") {
        assertFalse(message(referenceName + " must NOT be accessible!"), accessible)
      } else {
        assertTrue(message(referenceName + " must BE accessible!"), accessible)
      }

      if (options.contains(Applicable) && options(Applicable) == "false") {
        assertFalse(message(referenceName + " must NOT be applicable!"), applicable)
      } else {
        assertTrue(message(referenceName + " must BE applicable! " +
          result.get.problems.mkString("(", ",", ")")), applicable)
      }

      if (options.contains(Path)) {
        assertEquals(Path, options(Path), target.asInstanceOf[ScTypeDefinition].qualifiedName)
      }

      if (options.contains(File) || options.contains(Offset) || options.contains(Line)) {
        val actual = target.getContainingFile.getVirtualFile.getNameWithoutExtension
        val expected = if (!options.contains(File) || options(File) == "this") {
          reference.getElement.getContainingFile.getVirtualFile.getNameWithoutExtension
        } else options(File)
        assertEquals(File, expected, actual)
      }

      val expectedName = if (options.contains(Name)) options(Name) else referenceName
      assertEquals(Name, expectedName, target.name)

      if (options.contains(Line)) {
        assertEquals(Line, options(Line).toInt, lineOf(target))
      }

      if (options.contains(Offset)) {
        assertEquals(Offset, options(Offset).toInt, target.getTextOffset)
      }

      if (options.contains(Length)) {
        assertEquals(Length, options(Length).toInt, target.getTextLength)
      }

      if (options.contains(Type)) {
        val expectedClass = Class.forName(options(Type))
        val targetClass = target.getClass
        val text = Type + " - expected: " + expectedClass.getSimpleName + ", actual: " + targetClass.getSimpleName
        assertTrue(message(text), expectedClass.isAssignableFrom(targetClass))
      }
    }
  }

  private def lineOf(element: PsiElement) =
    element.getContainingFile.getText.substring(0, element.getTextOffset).count(_ == '\n') + 1

  private def format(text: String, message: String, line: Int) = {
    val lines = text.linesIterator.zipWithIndex.map(p => if (p._2 + 1 == line) p._1 + " // " + message else p._1)
    "\n\n" + lines.mkString("\n") + "\n"
  }
}

