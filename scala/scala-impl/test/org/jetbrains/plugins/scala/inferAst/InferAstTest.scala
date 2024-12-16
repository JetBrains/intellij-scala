package org.jetbrains.plugins.scala.inferAst

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt, PsiElementExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Paths
import scala.collection.mutable

class InferAstTest extends ScalaLightCodeInsightFixtureTestCase {
  def testInferRun(): Unit = {
    val testDataDir = Paths.get(TestUtils.getTestDataPath)
    val scalaImplPath = TestUtils.findCommunityRootPath / "scala/scala-impl"
    val scalaImplSrcPath = scalaImplPath / "src"

    println("Prepare files...")

    def copyDirFromScalaImpl(p: String): VirtualFile = {
      val path = scalaImplSrcPath / p
      assert(path.startsWith(scalaImplSrcPath), s"$path is not in scala-impl/src")
      myFixture.copyDirectoryToProject(
        testDataDir.relativize(path).toString,
        scalaImplSrcPath.relativize(path).toString
      )
    }

    def copyFileFromScalaImpl(p: String): VirtualFile = {
      val path = scalaImplSrcPath / p
      assert(path.startsWith(scalaImplSrcPath), s"$path is not in scala-impl/src")
      myFixture.copyFileToProject(
        testDataDir.relativize(path).toString,
        scalaImplSrcPath.relativize(path).toString
      )
    }

    myFixture.copyDirectoryToProject("inferAst", "")

    copyFileFromScalaImpl("org/jetbrains/plugins/scala/ScalaBundle.java")
    copyDirFromScalaImpl("org/jetbrains/plugins/scala/lang/lexer")
    val parserVF = copyDirFromScalaImpl("org/jetbrains/plugins/scala/lang/parser")

    val psiManager = PsiManager.getInstance(getProject)
    val blubVFile = parserVF.findFileByRelativePath("parsing/Blub.scala")
    assert(blubVFile != null)
    val blubPsiFile = psiManager.findFile(blubVFile)


    def allFiles(vf: VirtualFile): Seq[ScFile] = {
      if (vf.isDirectory) vf.getChildren.toSeq.flatMap(allFiles)
      else psiManager.findFile(vf).asOptionOf[ScFile].toSeq
    }


    println("Gather functions...")
    val analysis = new GlobalAnalysis(getProject)


    val allMethods =
      Seq(blubPsiFile).flatMap { file =>
        file.depthFirst(e => !e.is[ScFunctionDefinition])
          .collect { case m: ScFunctionDefinition => m }
          .flatMap(m => m.qualifiedNameOpt.map(_ -> m))
      }.toMap

    //allMethods.keys.toSeq.sorted.foreach(println)

    val blubMethod = allMethods("org.jetbrains.plugins.scala.lang.parser.parsing.Blub.parse")
    val obj = blubMethod.containingClass.asInstanceOf[ScObject]
    analysis.addToAnalysis(AnalysisItem(blubMethod, obj, Seq.empty))


//    allFiles(parserVF.findFileByRelativePath("parsing"))
//      .flatMap(findParseMethods)
//      .filter(isParseMethod)
//      .foreach { item =>
//        println(s"${item.obj.qualifiedName}: ${item.method.qualifiedNameOpt.get}")
//        analysis.addToAnalysis(item)
//      }


    println("Run analysis...")
    analysis.run()

    val resultItems = analysis.resultItems

    val functionResults = mutable.Map.empty[AnalysisItem, (AstAutomaton[ElementAstAction], Option[AstAutomaton[ElementAstAction]])]
    val elementResults = mutable.Map.empty[String, AstAutomaton[ElementAstAction]]

    def addElementResult(name: String, automaton: AstAutomaton[ElementAstAction]): Unit =
      elementResults.get(name) match {
        case Some(existing) => existing.merge(automaton)
        case None => elementResults.put(name, automaton)
      }

    for ((item, result) <- resultItems) {
      val (trueMain, trueElementTypes) = ElementAst.from(result.trueResult)
      trueElementTypes.foreach { case (name, aut) => addElementResult(name, aut) }

      val falseMain = result.falseResult.map { falseResult =>
        val (falseMain, falseElementTypes) = ElementAst.from(falseResult)
        falseElementTypes.foreach { case (name, aut) => addElementResult(name, aut) }
        falseMain
      }
      functionResults.put(item, (trueMain, falseMain))
    }

    println("Before Inlining Function Result:")
    functionResults.foreach { case (item, (trueMain, falseMain)) =>
      println(s"Before inlining $item (${if (falseMain.isDefined) "(true)" else ""})")
      println(trueMain.toGraphviz)
      println()

      falseMain.foreach { falseMain =>
        println(s"Before inlining $item (false)")
        println(falseMain.toGraphviz)
      }
    }

    println("Before Inlining Element Result:")
    elementResults.foreach { case (name, aut) =>
      println(s"Before inlining $name")
      println(aut.toGraphviz)
      println()
    }
  }

  private def findParseMethods(file: ScFile): Seq[AnalysisItem] =
    file
      .depthFirst(e => !e.is[ScObject])
      .collect { case obj: ScObject => obj }
      .flatMap { obj =>
        obj.allMethods.map(obj -> _.method)
      }
      .collect {
        case (obj, method: ScFunctionDefinition) => AnalysisItem(method, obj, Seq.empty)
      }
      .toSeq

  private def isParseMethod(analysisItem: AnalysisItem): Boolean = {
    val m = analysisItem.method
    m.parameters.forall(p => p.getType.getCanonicalText == "org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder")
  }

}
