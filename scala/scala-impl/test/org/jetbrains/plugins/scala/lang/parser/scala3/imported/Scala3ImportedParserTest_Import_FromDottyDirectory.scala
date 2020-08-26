package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import java.io.{File, PrintWriter}

import org.jetbrains.plugins.scala.util.TestUtils

import scala.io.Source

object Scala3ImportedParserTest_Import_FromDottyDirectory {

  /**
   * Imports Tests from the dotty repositiory
   */
  def main(args: Array[String]): Unit = {
    val dottyDirectory = args.headOption.getOrElse {
      println("no dotty directory specified")
      return
    }
    val srcDir = normalizeToAbsolutePath(dottyDirectory + "/tests/pos")

    val succDir = TestUtils.getTestDataPath + Scala3ImportedParserTest.directory
    val failDir = TestUtils.getTestDataPath +  Scala3ImportedParserTest_Fail.directory

    clearDirectory(succDir)
    clearDirectory(failDir)

    println("srcdir =  " + srcDir)
    println("faildir = " + failDir)

    new File(succDir).mkdirs()
    new File(failDir).mkdirs()

    for (file <- allFilesIn(srcDir) if file.toString.toLowerCase.endsWith(".scala")) {
      val target = failDir + file.toString.substring(srcDir.length).replace(".scala", "++++test")
      val content = {
        val src = Source.fromFile(file)
        try {
          val content = src.mkString
          content.replaceAll("[-]{5,}", "+") // <- some test files have comment lines with dashes which confuse junit
        } finally src.close()
      }

      val targetFile = new File(target)

      val targetWithDirs = failDir + "/" + Iterator
        .iterate(targetFile)(_.getParentFile)
        .takeWhile(_ != null)
        .takeWhile(!_.isDirectory)
        .map(_.getName.replace('.', '_').replace("++++", "."))
        .toSeq
        .reverse
        .mkString("_")
      println(file.toString + " -> " + targetWithDirs)

      val pw = new PrintWriter(targetWithDirs)
      pw.write(content)
      if (content.last != '\n')
        pw.write('\n')
      pw.println("-----")
      pw.close()
    }
  }

  def allFilesIn(path: String): Iterator[File] =
    allFilesIn(new File(path))

  def allFilesIn(path: File): Iterator[File] = {
    if (!path.exists) Iterator()
    else if (!path.isDirectory) Iterator(path)
    else path.listFiles.iterator.flatMap(allFilesIn)
  }

  private def clearDirectory(path: String): Unit =
    new File(path).listFiles().foreach(_.delete())

  private def normalizeToAbsolutePath(path: String): String =
    new File(path.replace("~", System.getProperty("user.home")) + "tests/pos").getAbsolutePath
}
