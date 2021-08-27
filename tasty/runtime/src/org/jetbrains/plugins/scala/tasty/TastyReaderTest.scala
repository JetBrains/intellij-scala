package org.jetbrains.plugins.scala.tasty

import org.jetbrains.plugins.scala.tasty.TreePrinter.textOf

import scala.util.control.NonFatal
//import org.junit.Assert

import java.io.File
import java.nio.file.{FileSystems, Files, Path}
import scala.collection.mutable

// TODO Convert to unit tests (depends on https://youtrack.jetbrains.com/issue/SCL-19023)
// TODO Convert ./data to test data
object TastyReaderTest {

  def main(args: Array[String]): Unit = {
    var passed, failed = Seq.empty[String]

    Seq(
      "annotation/Members",
      "annotation/Parameters",
      "annotation/Text",
      "member/Bounds",
      "member/Def",
      "member/ExtensionMethod",
      "member/Given",
      "member/InlineModifier",
      "member/Modifiers",
      "member/Qualifier",
      "member/This",
      "member/Type",
      "member/Val",
      "member/Var",
      "package1/package2/package",
      "package1/package2/Chained",
      "package1/package2/Flat",
      "package1/package2/Scope",
      "package1/Members",
      "package1/topLevel",
      "parameter/Bounds",
      "parameter/ByName",
      "parameter/CaseClass",
      "parameter/Class",
      "parameter/ContextBounds",
      "parameter/Def",
      "parameter/DefaultArguments",
      "parameter/Enum",
      "parameter/EnumCaseClass",
      "parameter/Extension",
      "parameter/ExtensionMethod",
      "parameter/Given",
      "parameter/HKT",
      "parameter/InlineModifier",
      "parameter/Modifiers",
      "parameter/Qualifier",
      "parameter/Repeated",
      "parameter/Trait",
      "parameter/Type",
      "parameter/Variance", // TODO TypeMember
      "typeDefinition/Class",
      "typeDefinition/Companions",
      "typeDefinition/Enum",
      "typeDefinition/ImplicitClass",
      "typeDefinition/Members",
      "typeDefinition/Modifiers",
      "typeDefinition/Object",
      "typeDefinition/Parents",
      "typeDefinition/Qualifier",
      "typeDefinition/Trait",
      "types/And",
      "types/Annotated",
      "types/Constant",
      "types/Function",
      "types/Ident",
      "types/Lambda",
      "types/Literal",
      "types/Or",
      "types/Projection",
      "types/Refs",
      "types/Select",
      "types/Singleton",
      "types/This",
      "types/Tuple",
      "types/Wildcard",
      "EmptyPackage",
      "Nesting",
    ).map("community/tasty/runtime/data/" + _ + ".scala").foreach { scalaFile =>
      assertExists(scalaFile)

      val tastyFile = {
        val packageFile = scalaFile.replaceFirst("\\.scala", "\\$package.tasty")
        if (exists(packageFile)) packageFile else scalaFile.replaceFirst("\\.scala", ".tasty")
      }
      assertExists(tastyFile)

      val tree = TreeReader.treeFrom(readBytes(tastyFile))

      val actual = try {
        textOf(tree)(using privateMembers = true)
      } catch {
        case NonFatal(e) =>
          Console.err.println(scalaFile)
          throw e
      }

      val expected = new String(readBytes(scalaFile))
        .replaceAll(raw"(?s)/\*\*/.*?/\*(.*?)\*/", "$1")

      val actualFile = scalaFile.replaceFirst("\\.scala", ".actual")

      if (actual != expected) {
        println(scalaFile)
        println("---Actual---")
        println(actual)
        println("---Expected---")
        println(expected)
        println("---")
        println("")
        Files.write(Path.of(actualFile), actual.getBytes)
        failed :+= scalaFile
      } else {
        if (exists(actualFile)) {
          Files.delete(Path.of(actualFile))
        }
        passed :+= scalaFile
      }

//      Assert.assertEquals(scalaFile, expected, actual)
    }
    if (failed.isEmpty) println(s"Tests passed: ${passed.length}")
    else Console.err.println(s"Tests passed: ${passed.length}, failed: ${failed.length}:\n" + failed.map("  " + _).mkString("\n"))
  }

  private def assertExists(path: String): Unit = assert(exists(path), path)

  private def exists(path: String): Boolean = new File(path).exists()

  private def readBytes(file: String): Array[Byte] = Files.readAllBytes(Path.of(file))
}
