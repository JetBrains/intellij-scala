package org.jetbrains.plugins.scala.lang.highlighting.decompiler

/**
  * @author Roman.Shein
  * @since 31.05.2016.
  */
class DecompilerHighlightingTest extends DecompilerHighlightingTestBase {

  def testSettings() = doTest("Settings.class")

  def testCommandLine() = doTest("CommandLine.class")

  def testDirectoryFileLookup() = doTest("DirectoryFileLookup.class")

  def testDefaultMacroCompiler() = doTest("DefaultMacroCompiler.class")

  def testFormatInterpolator() = doTest("FormatInterpolator.class")

  def testJavaParsers() = doTest("JavaParsers.class")

  def testContext() = doTest("Context.class")

  def testSymbolicXmlBuilder() = doTest("SymbolicXMLBuilder.class")

  def testSocketServer() = doTest("SocketServer.class")

  def testStdTags() = doTest("StdTags.class")
}