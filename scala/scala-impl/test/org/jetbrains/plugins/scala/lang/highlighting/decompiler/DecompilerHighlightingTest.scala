package org.jetbrains.plugins.scala.lang.highlighting.decompiler

import org.jetbrains.plugins.scala.ScalaVersion

class DecompilerHighlightingTest extends DecompilerHighlightingTestBase {

  def testSettings(): Unit = doTest("Settings.class")

  def testCommandLine(): Unit = doTest("CommandLine.class")

  def testDefaultMacroCompiler(): Unit = doTest("DefaultMacroCompiler.class")

  def testFormatInterpolator(): Unit = doTest("FormatInterpolator.class")

  def testContext(): Unit = doTest("Context.class")

  def testSymbolicXmlBuilder(): Unit = doTest("SymbolicXMLBuilder.class")

  def testSocketServer(): Unit = doTest("SocketServer.class")

  def testStdTags(): Unit = doTest("StdTags.class")
}

/**
 * The decompiled sources are highlighted against the scala-compiler of the test Scala version
 * (see `includeCompilerAsLibrary`), so these class files, which were compiled from the Scala 2.11
 * compiler sources, only resolve against the 2.11 compiler:
 *  - `DirectoryFileLookup` (compiled from `DirectoryFlatClassPath.scala`) references `FlatClassPath`
 *    and `ClassRepClassPathEntry`, both of which were removed in Scala 2.12
 *  - `JavaParsers` overrides `ParsersCommon.ParserCommon.deprecationWarning(offset, msg)`,
 *    which got an additional `since` parameter in Scala 2.12
 */
class DecompilerHighlightingTest_Scala2_11 extends DecompilerHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version <= ScalaVersion.Latest.Scala_2_11

  def testDirectoryFileLookup(): Unit = doTest("DirectoryFileLookup.class")

  def testJavaParsers(): Unit = doTest("JavaParsers.class")
}
