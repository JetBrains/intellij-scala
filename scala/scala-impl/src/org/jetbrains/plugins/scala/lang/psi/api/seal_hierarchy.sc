import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source

object Main {
  def allFilesIn(path: File): Iterator[File] = {
    if (!path.exists) Iterator()
    else if (!path.isDirectory) Iterator(path)
    else path.listFiles.sorted.iterator.flatMap(allFilesIn)
  }

  def allFilesInF(path: String): Iterator[File] =
    allFilesIn(new File(path))


  val basePath = "/home/tobi/workspace/intellij-scala/community/scala/scala-impl/src/org/jetbrains/plugins/scala/lang/psi/api"

  val allFiles = allFilesInF(basePath).filter(_.getName.endsWith(".scala")).toSeq


  //allFiles.foreach(f => println(f.getName))

  val classRegex = raw"trait\s+(Sc\w+)\s+(extends\s+[\w.]+(?:\s+with\s+[\w.]+)*)?\s*(\{)?\s*(})?".r

  case class Info(name: String, base: Option[String], accessPath: String, impls: Seq[String], companion: Boolean)

  def readFile(f: File): String = {
    val source = Source.fromFile(f)
    try source.getLines().mkString("\n")
    finally source.close()
  }

  def afterDot(s: String): String = {
    val i = s.lastIndexOf('.')
    if (i >= 0) s.substring(i + 1)
    else s
  }
  val extendZip = "extends" #:: LazyList.continually("with")

  def run(): Unit = {
    val allInfos = allFiles.iterator.flatMap { f =>
      var content = readFile(f)

      classRegex.findFirstMatchIn(content) match {
        case Some(m) =>
          println(m)
          val hasNonEmptyBraces = m.subgroups(2) != null && m.subgroups(3) == null
          val impls = Option(m.subgroups(1))
            .fold(Seq.empty[String])(_.split(raw"\s+").grouped(2).flatMap(_.lift(1)).toSeq)
          val name = m.subgroups.head

          val companionRegex = (s"object\\s+$name").r

          val companion = companionRegex.findFirstMatchIn(content).exists { cm =>
            assert(cm.start > m.start)
            content = content.patch(cm.start, s"abstract class ${name}Companion", cm.end - cm.start)
            true
          }

          if (hasNonEmptyBraces) {
            val parents = impls.map {
              case b if afterDot(b).startsWith("Sc") => b + "Base"
              case b => b
            }.zip(extendZip).map {
              case (b, ex) => s"$ex $b "
            }.mkString
            content = content.patch(m.start, s"trait ${name}Base $parents{ this: $name =>", m.end(3) - m.start)
          } else {
            content = content.patch(m.start, s"", m.end - m.start)
          }

          val idx = content.linesIterator.takeWhile(_.startsWith("package")).foldLeft(0)(_ + _.length + 1)

          content = content.patch(idx, "\nimport org.jetbrains.plugins.scala.lang.psi.api._\n\n", 0)

          println(name + " " + impls)
          if (hasNonEmptyBraces || companion) {
            Files.writeString(f.toPath, content)
          } else {
            f.delete()
          }

          val dir = f.getParentFile
          val packageFile = dir.toPath.resolve("package.scala").toFile

          val accessPath = if (dir.getAbsolutePath.length > basePath.length) {
            val accessPath = dir.getAbsolutePath.substring(basePath.length + 1).replace('/', '.')
            val redirect1 = s"  type $name = org.jetbrains.plugins.scala.lang.psi.api.$name\n"
            val redirect = if (!companion) redirect1 else s"$redirect1  val $name = org.jetbrains.plugins.scala.lang.psi.api.$name\n"

            if (packageFile.exists()) {
              val packageText = readFile(packageFile)

              val idx = packageText.lastIndexOf('}')
              assert(idx >= 0)

              Files.writeString(packageFile.toPath, packageText.patch(idx, redirect + "\n", 0))

            } else {
              val trimmed =
                Option(accessPath.lastIndexOf('.'))
                  .filter(_ >= 0)
                  .fold("")("." + accessPath.substring(0, _))
              Files.writeString(packageFile.toPath,
                s"""package org.jetbrains.plugins.scala.lang.psi.api$trimmed
                   |
                   |package object ${dir.getName} {
                   |$redirect
                   |}
                   |""".stripMargin)
            }
            accessPath + "."
          } else {
            ""
          }

          val info = Info(name, Option.when(hasNonEmptyBraces)(s"${name}Base"), accessPath, impls.map(afterDot), companion)
          Some(info)
        case _ =>
          None
      }
    } //.take(15)

    val builder = new StringBuilder

    builder ++=
      s"""package org.jetbrains.plugins.scala.lang.psi.api
        |
        |import com.intellij.psi.{PsiElement, PsiPackage}
        |
        |
        |""".stripMargin

    for (Info(name, base, accessPath, impls, hasCompanion) <- allInfos) {
      builder ++= s"trait $name" + extendZip.zip(base.map(accessPath + _) ++ impls).map { case (p, i) => s" $p $i"}.mkString
      builder += '\n'
      if (hasCompanion) {
        builder ++= s"object $name extends $accessPath${name}Companion\n"
      }
      builder ++= "\n"
    }

    Files.writeString(Paths.get(basePath + "/Hierarchy.scala"), builder.toString)
  }
}

Main.run()