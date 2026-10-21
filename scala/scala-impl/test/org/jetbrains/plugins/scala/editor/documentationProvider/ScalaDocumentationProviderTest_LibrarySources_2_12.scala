package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsScalaDocContentTesting

final class ScalaDocumentationProviderTest_LibrarySources_2_12 extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsScalaDocContentTesting {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
  override protected def includeScalaLibrarySources: Boolean = true

  /**
   * NOTE: this test supposed to work with scala 2.12 with a class containing macro from parent {{{
   * /** A base trait for sequences.
   * *  $seqInfo
   * */
   * trait Seq
   * }}}
   */
  def testMacro_FromInheritedLibrarySources_Scala_2_12(): Unit =
    doGenerateDocContentTest(
      s"""abstract class SeqUsageExample2 extends scala.collection.S${|}eq[String]""".stripMargin,
      """A base trait for sequences. Sequences are special cases of iterable collections of class <tt>Iterable</tt>.
        | Unlike iterables, sequences always have a defined order of elements. Sequences provide a method <tt>apply</tt> for indexing.
        | Indices range from <tt>0</tt> up to the <tt>length</tt> of a sequence.
        | Sequences support a number of methods to find occurrences of elements or subsequences, including
        |  <tt>segmentLength</tt>, <tt>prefixLength</tt>, <tt>indexWhere</tt>, <tt>indexOf</tt>,
        |  <tt>lastIndexWhere</tt>, <tt>lastIndexOf</tt>, <tt>startsWith</tt>, <tt>endsWith</tt>, <tt>indexOfSlice</tt>.
        |<p>
        |Another way to see a sequence is as a <tt>PartialFunction</tt> from <tt>Int</tt> values
        | to the element type of the sequence. The <tt>isDefinedAt</tt> method of a sequence returns <tt>true</tt> for the
        | interval from <tt>0</tt> until <tt>length</tt>.
        |</p>
        |<p>
        |Sequences can be accessed in reverse order of their elements,
        | using methods <tt>reverse</tt> and <tt>reverseIterator</tt>.
        |</p>
        |<p>
        |Sequences have two principal subtraits, <tt>IndexedSeq</tt> and <tt>LinearSeq</tt>, which give different
        | guarantees for performance. An <tt>IndexedSeq</tt> provides fast random-access of elements
        | and a fast <tt>length</tt> operation. A <tt>LinearSeq</tt> provides fast access only to the first element
        | via <tt>head</tt>, but also has a fast <tt>tail</tt> operation.
        |</p>
        |""".stripMargin
    )
}
