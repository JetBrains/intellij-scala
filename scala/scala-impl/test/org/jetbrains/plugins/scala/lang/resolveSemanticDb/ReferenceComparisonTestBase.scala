package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ComparisonTestBase.outPath
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase._

abstract class ReferenceComparisonTestBase_Scala3 extends ReferenceComparisonTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0
  // do not spam on in output in failed tests, we have too much of them currently
  override protected def reportFailedTestContextDetails: Boolean = false
}

abstract class ReferenceComparisonTestBase extends ComparisonTestBase {

  override def doTest(testName: String, shouldSucceed: Boolean): Unit = {
    val Result(problems, _, _, _, _, _) = runTestToResult(testName)

    if (shouldSucceed) {
      assert(problems.isEmpty, problems.mkString("\n"))
    } else {
      assert(problems.nonEmpty, "Expected some problems, but found none")
    }
  }

  protected def runTestToResult(testName: String): Result = {
    val files = setupFiles(testName)
    val store = SemanticDbStore(outPath.resolve(testName))

    var problems = Seq.empty[String]
    var refCount = 0
    var failedToResolve = 0
    var testedRefs = 0
    var completeCorrect = 0
    var partialCorrect = 0

    for (file <- files.filterByType[ScalaFile]) {
      val semanticDbFile = store.files.find(_.path.contains(file.name)).get
      val references = file.elements.filterByType[ScReference].toArray
      for (ref <- references) {
        refCount += 1
        val pos = textPosOf(ref.nameId)
        val refWithPos = s"${ref.refName} at ${pos.readableString} in ${file.name}"
        val resolved = ref.multiResolveScala(false).toSeq

        if (resolved.isEmpty) {
          problems :+= s"Couldn't resolve $refWithPos"
          failedToResolve += 1
        } else {
          val semanticDbReferences = semanticDbFile.referencesAt(pos)
          var didTest = false
          var atLeastOneSuccess = false
          var allSuccess = true

          for(semanticDbTarget <- semanticDbReferences.flatMap(_.symbol)) {
            didTest = true
            val ourTargets = resolved.flatMap(r => Seq(r.element) ++ r.parentElement).filterByType[ScNamedElement]
            if (!ourTargets.exists(posOfNavigationElementWithAdjustedEscapeId(_) == semanticDbTarget.position)) {
              val ours = ourTargets
                .map(e => s"${e.name} at ${textPosOf(e.getNavigationElement).readableString}")
                .mkString("\n")
              problems :+= s"$refWithPos resolves to $semanticDbTarget in semanticdb, but we resolve to:\n$ours"
              allSuccess = false
            } else {
              atLeastOneSuccess = true
            }
          }

          if (didTest) {
            testedRefs += 1
            if (allSuccess)
              completeCorrect += 1
            else if (atLeastOneSuccess)
              partialCorrect += 1
          }
        }
      }
    }

    Result(problems, refCount, failedToResolve, testedRefs, completeCorrect, partialCorrect)
  }
}

object ReferenceComparisonTestBase {
  case class Result(problems: Seq[String], refCount: Int, failedToResolve: Int, testedRefs: Int, completeCorrect: Int, partialCorrect: Int) {
    assert(testedRefs <= refCount)
    assert(completeCorrect + partialCorrect <= testedRefs)

    def incorrectResolves: Int = testedRefs - (completeCorrect + partialCorrect)

    def +(rhs: Result): Result = Result(
      problems ++ rhs.problems,
      refCount + rhs.refCount,
      failedToResolve + rhs.failedToResolve,
      testedRefs + rhs.testedRefs,
      completeCorrect + rhs.completeCorrect,
      partialCorrect + rhs.partialCorrect
    )
  }

  object Result {
    val empty: Result = Result(Seq.empty, 0, 0, 0, 0, 0)
  }

  def posOfNavigationElementWithAdjustedEscapeId(e: PsiNamedElement): TextPos = {
    val pos = textPosOf(e.getNavigationElement)
    if (e.name.startsWith("`")) pos.copy(col = pos.col + 1)
    else pos
  }
}