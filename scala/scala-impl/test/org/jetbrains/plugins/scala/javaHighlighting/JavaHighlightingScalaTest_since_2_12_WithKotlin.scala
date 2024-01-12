package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingScalaTest_since_2_12.addScalaPackageObjectDefinitions

class JavaHighlightingScalaTest_since_2_12_WithKotlin extends JavaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_12

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    super.librariesLoaders ++ Seq(
      IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.9.22")
    )
  }

  def testUseDeclarationsDefinedInScalaPackageObject_FromKotlinCode(): Unit = {
    addScalaPackageObjectDefinitions(myFixture.addFileToProject _)

    assertNoErrorsInKotlin(
      """object KotlinMain {
        |    @JvmStatic
        |    fun main(args: Array<String>) {
        |        //package object
        |        println(org.non_legacy.`package$`.`MODULE$`.fooStringType())
        |        //println(org.non_legacy.`package$`.`MODULE$`.fooInnerClassType()) //kotlinc error:  Cannot access class
        |
        |        //legacy package object
        |        println(org.legacy.`package$`.`MODULE$`.fooStringType())
        |        //println(org.legacy.`package$`.`MODULE$`.fooInnerClassType()) //kotlinc error:  Cannot access class
        |
        |        //ordinary object
        |        println(org.OrdinaryObject.fooStringType())
        |        //println(org.OrdinaryObject.fooInnerClassType()) //kotlinc error:  Cannot access class
        |
        |        println(org.`OrdinaryObject$`.`MODULE$`.fooStringType())
        |        //println(org.`OrdinaryObject$`.`MODULE$`.fooInnerClassType()) ////kotlinc error:  Cannot access class
        |    }
        |}
        |""".stripMargin
    )
  }
}
