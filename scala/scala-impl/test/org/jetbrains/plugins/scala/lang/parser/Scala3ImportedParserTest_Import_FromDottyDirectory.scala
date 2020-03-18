package org.jetbrains.plugins.scala
package lang
package parser

import java.io.{File, FilenameFilter, PrintWriter}

import org.jetbrains.plugins.scala.util.TestUtils

import scala.io.Source

object Scala3ImportedParserTest_Import_FromDottyDirectory {

  /**
   *  Imports Tests from the dotty repositiory
   */
  def main(args: Array[String]): Unit = {
    val dottyDirectory = args.headOption.getOrElse {
      println("no dotty directory specified")
      return
    }
    val failDir = TestUtils.getTestDataPath +  Scala3ImportedParserTest_Fail.directory
    val srcDir = dottyDirectory + "tests/pos"

    println("srcdir =  " + srcDir)
    println("faildir = " + failDir)

    for (file <- allFilesIn(srcDir)) {
      val target = failDir + file.toString.substring(srcDir.length).replace(".scala", "++++test")
      val content = {
        val src = Source.fromFile(file)
        try src.mkString finally src.close()
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
      println(file + " -> " + targetWithDirs)

      val pw = new PrintWriter(targetWithDirs)
      pw.write(content)
      if (content.last != '\n')
        pw.write('\n')
      pw.println("-----")
      pw.close()
    }
  }

  object ScalaFileFilter extends FilenameFilter {
    override def accept(dir: File, name: String): Boolean =
      dir.isDirectory || name.toLowerCase.endsWith(".scala")
  }

  def allFilesIn(path: String): Iterator[File] =
    allFilesIn(new File(path))

  def allFilesIn(path: File): Iterator[File] = {
    if (!path.exists) Iterator()
    else if (!path.isDirectory) Iterator(path)
    else path.listFiles(ScalaFileFilter).iterator.flatMap(allFilesIn)
  }
}
