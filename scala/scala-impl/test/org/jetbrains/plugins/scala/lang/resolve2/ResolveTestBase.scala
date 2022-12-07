package org.jetbrains.plugins.scala.lang.resolve2

import _root_.org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveTestCase}
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.junit.Assert._

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

/**
 * Also see [[org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase]]
 */
abstract class ResolveTestBase extends ScalaResolveTestCase {
  //Examples 1: /* file: this, offset: 5, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern */
  //Examples 2: /* type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */
  private val BlockCommentPattern = """/\*\s*(.*?)\s*\*/\s*""".r
  protected val ParametersGroupSeparator = "|||" //needed to support multiple resolve targets in expected data
  protected val ParametersGroupSeparatorRegex = raw"""\Q$ParametersGroupSeparator\E""".r

  private val Resolved = "resolved" // default: true
  private val Name = "name" // default: reference name
  private val File = "file" // default: this (if line or offset provided)
  private val Line = "line"
  private val Offset = "offset"
  private val Length = "length"
  private val Type = "type"
  private val Path = "path"
  private val Applicable = "applicable" // default: true
  private val Accessible = "accessible" // default: true

  protected def treatMultipleResolveResultsAsUnresolvedReference: Boolean = true

  private val Parameters = List(
    Resolved,
    Name,
    File,
    Line,
    Offset,
    Length,
    Type,
    Path,
    Applicable,
    Accessible
  )

  //each reference can be resolved to multiple targets
  protected case class ExpectedResolveResult(shouldBeResolved: Boolean, targets: Seq[TargetInfo])
  protected case class TargetInfo(map: Map[String, String]) {
    //NOTE: it would be better to move "resolved" from map of each target, this parameter affects all targets at once
    def shouldBeResolved: Boolean = !map.get(Resolved).contains("false")
  }
  protected case class ReferenceWithExpectedResolveResult(reference: PsiReference, expectedResolveResult: ExpectedResolveResult)

  private var referencesWithExpectedTargetInfo: Seq[ReferenceWithExpectedResolveResult] = Nil

  override def setUp(): Unit = {
    super.setUp()
    referencesWithExpectedTargetInfo = configureReferences()
  }

  override def folderPath: String = {
    super.folderPath + "resolve2/"
  }

  protected def configureReferences(): Seq[ReferenceWithExpectedResolveResult] = {
    val result = new ArrayBuffer[ReferenceWithExpectedResolveResult]

    val commentsMatches: Iterator[Regex.Match] =
      BlockCommentPattern.findAllIn(getFile.getText).matchData

    for (commentMatch <- commentsMatches) {
      val expectedTargetParameterGroupsText = commentMatch.group(1)
      val reference = getFile.findReferenceAt(commentMatch.end)
      assertNotNull("No reference found at offset " + commentMatch.end, reference)

      val targetInfosBuffer = new ArrayBuffer[TargetInfo]

      val expectedTargetParameterGroups = ParametersGroupSeparatorRegex.split(expectedTargetParameterGroupsText)
      for (parametersText <- expectedTargetParameterGroups) {
        val parameters = parseParameters(parametersText)
        assertKnown(parameters)
        targetInfosBuffer += parameters
      }
      val targetInfos = targetInfosBuffer.toSeq
      val expectedResolveResult = ExpectedResolveResult(shouldBeResolved = targetInfos.forall(_.shouldBeResolved), targetInfos)
      result += ReferenceWithExpectedResolveResult(reference, expectedResolveResult)
    }

    assertTrue("At least one reference is expected", result.nonEmpty)
    assertTrue("Every resolved reference must have at least one expected target", result.forall { x =>
      !x.expectedResolveResult.shouldBeResolved || x.expectedResolveResult.targets.nonEmpty
    })

    result.toSeq
  }

  protected def assertKnown(targetInfo: TargetInfo): Unit = {
    for ((key, _) <- targetInfo.map) {
      assertTrue("Unknown parameter: " + key + "\nAllowed: " + Parameters.mkString(", "),
        Parameters.contains(key))
    }
  }

  protected def parseParameters(s: String): TargetInfo = {
    val map: Map[String, String] =
      if (s.isEmpty) Map()
      else {
        val parameters = s.split("""\s*,\s*""").map(_.trim)
        val seq = parameters.toSeq.map { parameterDefinition =>
          val parts = parameterDefinition.split("""\s*:\s*""")
          (parts(0), parts(1))
        }
        Map(seq: _*)
      }

    TargetInfo(map)
  }

  def doTest(): Unit = try {
    doTestImpl()
  } catch {
    case t: Throwable =>
      System.err.println(s"Test file: $testFilePath")
      throw t
  }

  private def doTestImpl(): Unit = {
    referencesWithExpectedTargetInfo.zipWithIndex.foreach { case (ReferenceWithExpectedResolveResult(reference, expectedResolvResult), referenceIndex) =>
      reference match {
        case ref: ScReference =>
          doEachTest(ref, referenceIndex, expectedResolvResult)
        case ref: PsiMultiReference =>
          val hostReferences = ref.getReferences
          if (hostReferences.length == 2) {
            hostReferences.find(_.isInstanceOf[ScReference]) match {
              case Some(r: ScReference) =>
                doEachTest(r, referenceIndex, expectedResolvResult)
              case _ =>
                assert(assertion = false, message = "Multihost references are not supported")
            }
          } else {
            assert(assertion = false, message = "Multihost references are not supported")
          }
      }
    }
  }

  protected def doEachTest(reference: ScReference, referenceIndex: Int, expectedResolveResult: ExpectedResolveResult): Unit = {
    val referenceName = reference.refName

    val resolveResults0: Seq[ScalaResolveResult] =
      reference.multiResolveScala(false).toSeq.sortBy(_.element.getTextOffset)

    val resolveResults: Seq[ScalaResolveResult] =
      if (treatMultipleResolveResultsAsUnresolvedReference && resolveResults0.length > 1)
        Nil
      else
        resolveResults0

    def message0(innerMessage: String): String =
      s"""$innerMessage (referenceIndex: $referenceIndex)
         |${format(getFile.getText, innerMessage, lineOf(reference))}""".stripMargin

    if (!expectedResolveResult.shouldBeResolved) {
      assertTrue(message0(referenceName + " must NOT be resolved!"), resolveResults.isEmpty)
      return
    }

    assertEquals(
      message0(s"Wrong number of resolved targets for: $referenceName"),
      expectedResolveResult.targets.length,
      resolveResults.length
    )

    for (((expectedTarget, resolveResult), idx) <- expectedResolveResult.targets.zip(resolveResults).zipWithIndex) {
      assertResolveResult(expectedTarget, resolveResult, idx)
    }

    def assertResolveResult(expectedTarget: TargetInfo, result: ScalaResolveResult, targetIdx: Int): Unit = {

      def message(innerMessage: String): String =
        s"""$innerMessage  (reference index: $referenceIndex, expected target index: $targetIdx)
           |${format(getFile.getText, innerMessage, lineOf(reference))}""".stripMargin

      val options: Map[String, String] = expectedTarget.map

      val (target, accessible, applicable) = (
        result.get.element,
        result.get.isAccessible,
        result.get.isApplicable()
      )

      def assertParameterEquals(name: String, expected: Any, actual: Any): Unit = {
        if (expected != actual)
          fail(message(s"$name - expected: $expected, actual: $actual"))
      }

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
        assertParameterEquals(Path, options(Path), target.asInstanceOf[ScTypeDefinition].qualifiedName)
      }

      if (options.contains(File) || options.contains(Offset) || options.contains(Line)) {
        val actual = target.getContainingFile.getVirtualFile.getNameWithoutExtension
        val expected = if (!options.contains(File) || options(File) == "this") {
          reference.getElement.getContainingFile.getVirtualFile.getNameWithoutExtension
        } else options(File)
        assertParameterEquals(File, expected, actual)
      }

      val expectedName = if (options.contains(Name)) options(Name) else referenceName
      assertParameterEquals(Name, expectedName, target.name)

      if (options.contains(Line)) {
        assertParameterEquals(Line, options(Line).toInt, lineOf(target))
      }

      if (options.contains(Offset)) {
        assertParameterEquals(Offset, options(Offset).toInt, target.getTextOffset)
      }

      if (options.contains(Length)) {
        assertParameterEquals(Length, options(Length).toInt, target.getTextLength)
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

