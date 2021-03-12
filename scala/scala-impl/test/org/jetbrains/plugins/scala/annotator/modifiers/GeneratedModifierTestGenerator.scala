package org.jetbrains.plugins.scala
package annotator.modifiers

import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.util.TestUtils

import extensions._
import java.nio.file.{Files, Paths}
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.sys.process.{Process, ProcessLogger}

object GeneratedModifierTestGenerator {
  def generatedModifiersTestFilePath: String = (TestUtils.getTestDataPath + "/annotator/modifiers/generated-modifiers.test").withNormalizedSeparator

  private val name = "$name$"
  private val modifiers = "$modifiers$"
  private val content = "$content$"

  private val outers = Seq(
    s"$modifiers class $name() { $content }",
    s"$modifiers trait $name { $content }",
    s"$modifiers object $name { $content }",
    s"object $name { { $content } }",
  )

  private val inners = Seq(
    //s"$modifiers def $name(): Unit",
    s"$modifiers def $name(): Unit = ()",
    //s"$modifiers val $name: Int",
    s"$modifiers val $name: Int = 0",
    //s"$modifiers var $name: Int",
    s"$modifiers var $name: Int = 0",
  ) ++ outers.filter(canHaveModifiers).map(_.replace(content, ""))

  private val scala2Modifiers = {
    import ScalaModifier._
    Seq(Private, Protected, Final, Abstract, Override, Implicit, Sealed, Lazy, Case).map(_.text())
  }

  private val scala3Modifiers = {
    import ScalaModifier._
    Seq(Inline, Transparent, Open, Opaque, Infix).map(_.text())
  }

  private def scala2ModifierCombinations: Seq[String] =
    Seq("") ++ scala2Modifiers ++ (
      for (m1 <- scala2Modifiers; m2 <- scala2Modifiers)
        yield s"$m1 $m2"
    )

  private def canHaveModifiers(code: String): Boolean = code.contains(modifiers)

  private def hasError(code: String): Boolean = {
    val tmp = Files.createTempDirectory("modifier-checker-gen")
    val codePath = tmp.resolve("code.scala")
    Files.writeString(codePath, code)
    val logger = ProcessLogger(_ => ())
    // todo: do it better... it's not super slow but it could be a lot (!) faster
    Process(Seq("scalac", "-stop:typer", codePath.toAbsolutePath.toString), tmp.toFile).!(logger) != 0
  }

  def main(args: Array[String]): Unit = {
    val executor = Executors.newFixedThreadPool(16)
    implicit val executorCtx: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(executor)

    val futures = for (outer <- outers) yield {
      val modifiersForOuter =
        if (canHaveModifiers(outer)) scala2ModifierCombinations
        else scala2ModifierCombinations.take(1)
      for (m <- modifiersForOuter) yield Future {
        val outerCode = outer
          .replace(name, "outer")
          .replace(modifiers, m)
        val code = outerCode.replace(content, "")
        val err = hasError(code)
        println(err.toString + ": " + code)

        Seq(Future.successful(err, code)) ++ (if (!err) {
          for (inner <- inners; m <- Seq("", "abstract override") ++ scala2Modifiers) yield Future {
            val innerCode = inner
              .replace(name, "inner")
              .replace(modifiers, m)
              .replace(content, "")

            val code = outerCode.replace(content, innerCode)
            val err = hasError(code)
            println(err.toString + ": " + code)
            (err, code)
          }
        } else Seq.empty)
      }
    }


    val x = Future.sequence(futures.flatten).flatMap(x => Future.sequence(x.flatten))
    val result = Await.result(x, Duration.Inf)
    println(result.size)
    Files.writeString(
      Paths.get(generatedModifiersTestFilePath),
      result.map { case (hasError, code) => s"// ${!hasError}\n$code"}.mkString("\n")
    )
    executor.shutdown()
  }
}
