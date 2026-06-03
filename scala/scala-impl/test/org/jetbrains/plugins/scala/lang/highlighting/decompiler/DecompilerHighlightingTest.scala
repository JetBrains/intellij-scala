package org.jetbrains.plugins.scala.lang.highlighting.decompiler

class DecompilerHighlightingTest extends DecompilerHighlightingTestBase {

  def testSettings(): Unit = doTest("Settings.class")

  def testCommandLine(): Unit = doTest("CommandLine.class")

  def testDirectoryFileLookup(): Unit = doTest("DirectoryFileLookup.class")

  def testDefaultMacroCompiler(): Unit = doTest("DefaultMacroCompiler.class")

  def testFormatInterpolator(): Unit = doTest("FormatInterpolator.class")

  def testJavaParsers(): Unit = doTest("JavaParsers.class")

  def testContext(): Unit = doTest("Context.class")

  def testSymbolicXmlBuilder(): Unit = doTest("SymbolicXMLBuilder.class")

  def testSocketServer(): Unit = doTest("SocketServer.class")

  def testStdTags(): Unit = doTest("StdTags.class")
}