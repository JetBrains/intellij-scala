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

//    allFiles(parserVF.findFileByRelativePath("parsing"))
//      .flatMap(findParseMethods)
//      .filter(isParseMethod)
//      .foreach(analysis => println(s"${analysis.obj.qualifiedName}: ${analysis.method.qualifiedNameOpt.get}"))


    println("Gather functions...")


    val allMethods =
      Seq(blubPsiFile).flatMap { file =>
        file.depthFirst(e => !e.is[ScFunctionDefinition])
          .collect { case m: ScFunctionDefinition => m }
          .flatMap(m => m.qualifiedNameOpt.map(_ -> m))
      }.toMap

    //allMethods.keys.toSeq.sorted.foreach(println)

    println("Run analysis...")
    val analysis = new GlobalAnalysis(getProject)
    val blubMethod = allMethods("org.jetbrains.plugins.scala.lang.parser.parsing.Blub.parse")
    val obj = blubMethod.containingClass.asInstanceOf[ScObject]
    analysis.addToAnalysis(AnalysisItem(blubMethod, obj, Seq.empty))

    analysis.run()
  }

  def findParseMethods(file: ScFile): Seq[AnalysisItem] =
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

  def isParseMethod(analysisItem: AnalysisItem): Boolean = {
    val m = analysisItem.method
    m.parameters.forall(p => p.isImplicit && p.getType.getCanonicalText == "org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder")
  }
}
