package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ComparisonTestBase.outPath
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase._
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

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
    val store = SemanticDbStore.fromTextFile(outPath.resolve(testName + ".semdb"))

    var problems = Seq.empty[String]
    var refCount = 0
    var failedToResolve = 0
    var testedRefs = 0
    var completeCorrect = 0
    var partialCorrect = 0

    for (file <- files.filterByType[ScalaFile]) {
      val semanticDbFile = store.files.find(_.path.contains(file.name)).get
      val references = file
        .depthFirst(!_.is[ScImportStmt]) // don't look into ScImportStmt, some weird stuff is going on in semanticdb
        .filterByType[ScReference]
        .map(RefInfo.fromRef)
        .toSeq
      val implicitArgs = file
        .depthFirst()
        .filterByType[ImplicitArgumentsOwner]
        .flatMap(RefInfo.forImplicitArguments)
      for (ref <- references ++ implicitArgs) {
        refCount += 1

        if (ref.failedToResolve) {
          problems :+= s"Couldn't resolve $ref" + ref.problems.fold("")(" (Problems:" + _ + ")")
          failedToResolve += 1
        } else {
          val semanticDbReferences = semanticDbFile.referencesAt(ref.pos, empty = ref.isImplicit)
          var didTest = false
          var atLeastOneSuccess = false
          var allSuccess = true
          var newProblems = List.empty[String]

          if (!ref.targets.exists(target => isInRefinement(target.element))) {
            def ignoreSemanticDbRef(ref: SDbRef): Boolean = {
              // ignore locals and implicits involving ClassTag
              ref.pointsToLocal || ref.symbol.contains("ClassTag")
            }

            for(semanticDbRef <- semanticDbReferences if !ignoreSemanticDbRef(semanticDbRef)) {
              didTest = true
              val semanticDbTargetPos = semanticDbRef.targetPosition
              val semanticDbTargetSymbol = ComparisonSymbol.fromSemanticDb(semanticDbRef.symbol)
              val textFits = ref.targets.exists(_.symbol == semanticDbTargetSymbol)
              val positionFits = semanticDbTargetPos.exists(ref.targets.map(_.adjustedPosition).contains)

              if (!textFits && !positionFits) {
                val ours = ref.targets
                  .map(target => s"${target.symbol} at ${target.position}")
                  .mkString("\n")
                val semPos = semanticDbTargetPos.fold("<no position>")(_.toString)
                newProblems :+= s"$ref resolves to $semanticDbTargetSymbol in semanticdb ($semPos), but we resolve to:\n$ours"
                allSuccess = false
              } else {
                atLeastOneSuccess = true
              }
            }
          }

          if (didTest) {
            testedRefs += 1
            if (ref.isImplicit && atLeastOneSuccess) {
              completeCorrect += 1
            } else {
              problems ++= newProblems
              if (allSuccess)
                completeCorrect += 1
              else if (atLeastOneSuccess)
                partialCorrect += 1
            }
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
    val pos = TextPos.of(e.getNavigationElement)
    if (Option(e.name).exists(_.startsWith("`"))) pos.copy(col = pos.col + 1)
    else pos
  }

  trait RefTarget {
    def element: PsiNamedElement
    lazy val symbol: String = ComparisonSymbol.fromPsi(element)
    def adjustedPosition: TextPos = posOfNavigationElementWithAdjustedEscapeId(element)
    def position: TextPos = TextPos.of(element.getNavigationElement)
  }

  case class PhysicalRefTarget(element: PsiNamedElement) extends RefTarget
  case class AssignmentRefTarget(element: PsiNamedElement) extends RefTarget {
    override lazy val symbol: String =
      ComparisonSymbol.fromPsi(element)
        .stripSuffix(s"${element.name}.")
        .stripSuffix(s"`${element.name}`.")
        .appendedAll(s"`${element.name}_=`().")
  }

  case class RefInfo(name: String,
                     pos: TextPos,
                     resolved: Seq[ScalaResolveResult],
                     fileName: String,
                     problems: Option[String],
                     isImplicit: Boolean) {
    override def toString: String = s"$name at $pos in $fileName"

    lazy val targets: Seq[RefTarget] = resolved
      .flatMap(r => Seq(r.element) ++ r.parentElement)
      .filterByType[PsiNamedElement]
      .flatMap {
        case td: ScReferencePattern if td.isVar && td.containingClass != null =>
          Seq(PhysicalRefTarget(td), AssignmentRefTarget(td))
        case field: ScFieldId if field.isVar =>
          Seq(PhysicalRefTarget(field), AssignmentRefTarget(field))
        case param: ScClassParameter if param.isVar =>
          Seq(PhysicalRefTarget(param), AssignmentRefTarget(param))
        case typeDef: ScTypeAliasDefinition if !typeDef.hasModifierPropertyScala("opaque") =>
          val defs = Seq(typeDef) :++ typeDef.aliasedType.toOption.flatMap(_.extractClass)
          defs.map(PhysicalRefTarget)
        case x =>
          Seq(PhysicalRefTarget(x))
      }

    /*lazy val problems: Option[String] = {
      val resultsWithProblems = resolved.filter(_.problems.nonEmpty)
      if (resultsWithProblems.nonEmpty && resultsWithProblems.sizeIs == resolved.size)
        Some(resultsWithProblems.map(rr => rr.problems.mkString(" and ") + s" for ${rr.name}").mkString(", "))
      else None
    }*/
    def failedToResolve: Boolean = resolved.isEmpty || problems.nonEmpty
  }

  object RefInfo {
    def fromRef(ref: ScReference): RefInfo =
      RefInfo(
        ref.refName,
        TextPos.of(ref.nameId),
        ref.multiResolveScala(false).toSeq,
        ref.getContainingFile.name,
        None,
        isImplicit = false
      )

    def forImplicitArguments(iao: ImplicitArgumentsOwner): Seq[RefInfo] = {
      iao.findImplicitArguments match {
        case Some(iargs) =>
          iargs.zipWithIndex.flatMap { case (rr, i) =>
            val file = iao.getContainingFile
            Some(RefInfo(
              s"implicit-param:$i",
              TextPos.at(iao.endOffset, file),
              Seq(rr),
              file.name,
              rr.problems match {
                case Seq() => None
                case problems => Some(problems.mkString(", "))
              },
              isImplicit = true
            ))
          }
        case None =>
          Seq.empty
      }
    }
  }
}