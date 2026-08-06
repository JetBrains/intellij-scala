package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.{PsiClassOwner, PsiElement, PsiFile, PsiNamedElement}
import com.intellij.testFramework.TestLoggerKt
import com.jetbrains.rd.util.threading.CompoundThrowable
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScMatchTypeElement, ScTypeElement, ScTypeLambdaTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScModifierList, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScQuoted, ScSpliced}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumClassCase, ScExtension, ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportSelector, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.Context
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase.RefInfo.{assignmentTarget, opaqueTarget, physicalRefTarget}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase._
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.Symbol._
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations.ReferenceComparisonTestConfig
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.junit.Assert

import scala.collection.mutable.ArrayBuffer

abstract class ReferenceComparisonTestBase(config: ReferenceComparisonTestConfig) extends ComparisonTestBase(config) {
  override protected def reportFailedTestContextDetails: Boolean = false

  override protected lazy val projectJdk: Sdk =
    SmartJDKLoader.createFilteredJdk(LanguageLevel.JDK_21, Seq("java.base", "java.compiler", "java.rmi", "java.sql", "java.desktop"))

  override def doTest(testName: String, shouldSucceed: Boolean): Unit = {
    val Result(actualProblems, _, _, _, _, _, _) = runTestToResult(testName)

    if (shouldSucceed) {
      assert(actualProblems.isEmpty, actualProblems.mkString("\n"))
    } else {
      println(actualProblems.mkString("\n"))
      assert(actualProblems.nonEmpty, "Expected some problems, but found none")
    }
  }

  protected def runTestToResult(testName: String): Result = {
    val files = setupFiles(testName)
    val semDbFilePath = possibleStoreNames(testName)
      .map(config.outPath.resolve)
      .find(_.exists)
      .getOrElse(throw new IllegalStateException(s"No semdb file found for: $testName. Possible filenames: ${possibleStoreNames(testName).mkString(", ")}"))
    val store = SemanticDbStore.fromTextFile(semDbFilePath)

    var problems = Seq.empty[String]
    var refCount = 0
    var failedToResolve = 0
    var testedRefs = 0
    var completeCorrect = 0
    var partialCorrect = 0

    for (file <- files.filterByType[ScalaFile]) {
      val semanticDbFile = store.files.find(_.path.contains(file.name)).getOrElse {
        throw new RuntimeException(
          s"""Can't find semanticdb file for ${file.name} among files:
             |${store.files.map(_.path).mkString("\n")}""".stripMargin
        )
      }
      val references = file
        .depthFirst(!_.is[ScImportStmt]) // don't look into ScImportStmt, some weird stuff is going on in semanticdb
        .filterByType[ScReference]
        .toSeq

      val implicitParameterOwners = file
        .depthFirst()
        .filterByType[ImplicitArgumentsOwner]
        .toSeq

      val referencesInfo = references.map(RefInfo.fromRef)
      val implicitArgs = implicitParameterOwners.flatMap(RefInfo.forImplicitArguments)

      for (ref <- referencesInfo ++ implicitArgs) {
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

          if (!ref.targets.exists(
            target => isInRefinement(target.element) || target.isDynamic)
          ) {
            def ignoreSemanticDbRef(ref: SDbRef): Boolean = {
              // ignore locals and implicits involving ClassTag
              ref.pointsToLocal ||
                ref.symbol.contains("ClassTag") ||
                // there are lots of edge cases related to resolving type parameters
                // (e.g. context bounds that have no reference in source code, but do have it in compiler expanded ast)
                // on the other hand, I think it's safe to assume we resolve references to type parameters correctly,
                // as it is just a simple tree walk-up 99.999% of the time
                ref.isTypeParamRef
            }

            for (semanticDbRef <- semanticDbReferences if !ignoreSemanticDbRef(semanticDbRef)) {
//              assertResolves(semanticDbRef.symbol, ref.targets.map(_.element))
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


    val errorLog = TestLoggerKt.getErrorLog
    if (errorLog != null) {
      val errors = errorLog.takeLoggedErrors()
      if (!errors.isEmpty) {
        throw new CompoundThrowable(errors)
      }
    }

    val tags = files.filterByType[ScalaFile].flatMap(collectFeaturesIn).distinct
    Result(problems, refCount, failedToResolve, testedRefs, completeCorrect, partialCorrect, tags)
  }

  /**
   *  Tests SemanticDB [[Symbol]] to `PsiElement` resolution, #SCL-25458
   */
  private def assertResolves(symbol: String, targets: Seq[PsiElement]): Unit = {
    val path = parse(symbol)
    val element = resolve(None, path)(getProject)

    val expected = targets.map {
      case p: ScPackageImpl => p.pack
      case e => e
    }

    val actual = element.toOption.getOrElse("none")

    val message =
      s"""
         |Symbol: $symbol
         |Path: ${path.mkString(", ")}
         |Expected: ${expected.mkString(", ")}
         |Actual: $actual
         |""".stripMargin

    print(message)

    if (symbol != "java/lang/String#`+`().") {
      Assert.assertTrue(message, expected.contains(actual))
    }
  }
}

object ReferenceComparisonTestBase {

  case class Result(problems: Seq[String],
                    refCount: Int,
                    failedToResolve: Int,
                    testedRefs: Int,
                    completeCorrect: Int,
                    partialCorrect: Int,
                    tags: Seq[String]) {
    assert(testedRefs <= refCount)
    assert(completeCorrect + partialCorrect <= testedRefs)

    def incorrectResolves: Int = testedRefs - (completeCorrect + partialCorrect)

    def +(rhs: Result): Result = Result(
      problems ++ rhs.problems,
      refCount + rhs.refCount,
      failedToResolve + rhs.failedToResolve,
      testedRefs + rhs.testedRefs,
      completeCorrect + rhs.completeCorrect,
      partialCorrect + rhs.partialCorrect,
      tags ++ rhs.tags
    )
  }

  object Result {
    val empty: Result = Result(Seq.empty, 0, 0, 0, 0, 0, Seq.empty)
  }

  def posOfNavigationElementWithAdjustedEscapeId(e: PsiNamedElement): TextPos = {
    val pos = TextPos.of(e.getNavigationElement)
    if (Option(e.name).exists(_.startsWith("`"))) pos.copy(col = pos.col + 1)
    else pos
  }

  private def collectFeaturesIn(file: ScalaFile): Seq[String] = {
    val all = ArrayBuffer.empty[String]
    file.depthFirst().foreach {
      case _: ScMatchTypeElement                              => all += "matchType"
      case ScInfixTypeElement(_, ElementText("&"), _)         => all += "unionType"
      case ScInfixTypeElement(_, ElementText("|"), _)         => all +="intersectionType"
      case _: ScExportStmt                                    => all += "export"
      case i: ScImportSelector if i.isGivenSelector           => all += "givenImport"
      case t: ScTrait if t.parameters.nonEmpty                => all += "traitParameters"
      case _: ScSpliced | _: ScQuoted                         => all += "spliced/quoted"
      case m: ScModifierList if m.isInline && m.isTransparent => all += "transparentInline"
      case m: ScModifierList if m.isOpaque                    => all += "opaque"
      case _: ScTypeLambdaTypeElement                         => all += "typeLambda"
      case _: ScExtension                                     => all += "extension"
      case t: ScTypeElement if t.textMatches("AnyKind")       => all += "anykind"
      case p: ScParameter if isByNameImplicit(p)              => all += "byNameImplicit"
      case _: ScEnum                                          => all += "enum"
      case _: ScDerivesClause                                 => all += "derives"
      case _ =>
    }
    all.toSeq
  }

  private def isByNameImplicit(p: ScParameter) = p.isImplicit && p.isCallByNameParameter

  trait RefTarget {
    def element: PsiNamedElement
    lazy val symbol: String = ComparisonSymbol.fromPsi(element)
    def adjustedPosition: TextPos = posOfNavigationElementWithAdjustedEscapeId(element)
    def position: TextPos = TextPos.of(element.getNavigationElement)

    def isDynamic: Boolean = element match {
      case fn: ScFunction =>
        DynamicResolveProcessor.APPLY_DYNAMIC == fn.name || DynamicResolveProcessor.APPLY_DYNAMIC_NAMED == fn.name
      case _ => false
    }
  }

  case class PhysicalRefTarget(element: PsiNamedElement) extends RefTarget

  case class ExportedRefTarget(originalElement: PsiNamedElement, stringRepr: String) extends RefTarget {
    override lazy val symbol: String = stringRepr
    override def element: PsiNamedElement = originalElement
  }

  case class AssignmentRefTarget(element: PsiNamedElement) extends RefTarget {
    override lazy val symbol: String =
      ComparisonSymbol.fromPsi(element)
        .stripSuffix(".")
        .stripSuffix("()")
        .stripSuffix(s"${element.name}")
        .stripSuffix(s"`${element.name}`")
        .appendedAll(s"`${element.name}_=`().")
  }

  case class RefInfo(
    name: String,
    pos: TextPos,
    resolved: Seq[ScalaResolveResult],
    fileName: String,
    problems: Option[String],
    isImplicit: Boolean,
    isScalaDocRef: Boolean,
    isExportImportRef: Boolean
  )(implicit context: Context) {
    override def toString: String = s"$name at $pos in $fileName"

    lazy val targets: Seq[RefTarget] = resolved.flatMap(targetsForResolveResult)

    private def targetsForResolveResult(resolveResult: ScalaResolveResult): Seq[RefTarget] = {
      val exportedForwarderSymbol = RefInfo.exportedForwarderSymbol(resolveResult)

      (Seq(resolveResult.element) ++ resolveResult.parentElement ++ resolveResult.innerResolveResult.map(_.element))
        .filterByType[PsiNamedElement]
        .flatMap { named =>
          val exportedTarget =
            if (named eq resolveResult.element) exportedForwarderSymbol.map(ExportedRefTarget(named, _))
            else                                None

          physicalRefTarget(named) ++ assignmentTarget(named) ++ opaqueTarget(named) ++ exportedTarget
        }
    }

    /*lazy val problems: Option[String] = {
      val resultsWithProblems = resolved.filter(_.problems.nonEmpty)
      if (resultsWithProblems.nonEmpty && resultsWithProblems.sizeIs == resolved.size)
        Some(resultsWithProblems.map(rr => rr.problems.mkString(" and ") + s" for ${rr.name}").mkString(", "))
      else None
    }*/
    def failedToResolve: Boolean = {
      resolved.isEmpty ||
        (resolved.size > 1 && !isScalaDocRef && !isExportImportRef) ||
        problems.nonEmpty
    }
  }

  object RefInfo {
    def fromRef(ref: ScReference): RefInfo = {
      val resolveResult = ref.multiResolveScala(false).toSeq
      val problems = None
      RefInfo(
        ref.refName,
        TextPos.of(ref.nameId),
        resolveResult,
        ref.getContainingFile.name,
        problems,
        isImplicit = false,
        isScalaDocRef = ref.parentOfType[ScDocComment].isDefined,
        isExportImportRef = ref.parentOfType[ScImportOrExportStmt].isDefined
      )(Context(ref))
    }

    def forImplicitArguments(iao: ImplicitArgumentsOwner): Seq[RefInfo] = {
      val file = iao.getContainingFile
      iao.findImplicitArguments.zipWithIndex.flatMap { case (implicitArgClause, clauseIdx) =>
        implicitArgClause.args.zipWithIndex.flatMap { case (rr, i) =>
          val problems = rr.problems match {
            case Seq() => None
            case problems => Some(problems.mkString(", "))
          }
          Some(RefInfo(
            s"implicit-param:$clauseIdx:$i",
            TextPos.at(iao.endOffset, file),
            Seq(rr),
            file.name,
            problems,
            isImplicit = true,
            isScalaDocRef = false,
            isExportImportRef = false,
          )(Context(iao)))
        }
      }
    }

    private def physicalRefTarget(resolved: PsiNamedElement): Option[PhysicalRefTarget] = resolved match {
      case f: ScFunction if f.name == "apply" && f.isSynthetic && f.containingClass.is[ScEnumClassCase] => None
      case _ => Some(PhysicalRefTarget(resolved))
    }

    private def assignmentTarget(resolved: PsiNamedElement): Option[AssignmentRefTarget] = resolved match {
      case td: ScReferencePattern if td.isVar && td.containingClass != null =>
        Some(AssignmentRefTarget(td))
      case field: ScFieldId if field.isVar =>
        Some(AssignmentRefTarget(field))
      case param: ScClassParameter if param.isVar =>
        Some(AssignmentRefTarget(param))
      case fun: ScFunction if fun.isParameterless && hasSetter(fun) => Some(AssignmentRefTarget(fun))
      case _ => None
    }

    private def hasSetter(fun: ScFunction): Boolean = Option(fun.containingClass).exists {
      _.allFunctionsByName(fun.name + "_=").nonEmpty
    }

    private def opaqueTarget(resolved: PsiNamedElement)(implicit context: Context): Option[PhysicalRefTarget] = resolved match {
      case typeDef: ScTypeAliasDefinition if !typeDef.isEffectivelyOpaque =>
        val aliased = typeDef.aliasedType.toOption.flatMap(_.extractClass)
        aliased.map(PhysicalRefTarget)
      case _ => None
    }

    private def exportedForwarderSymbol(resolveResult: ScalaResolveResult): Option[String] = {
      for {
        exportedInfo         <- resolveResult.exportedInfo
        forwarderOwnerSymbol <- exportOwnerSymbol(exportedInfo.exportedIn)
      } yield {
        val renamedMemberPart = resolveResult.renamed
        val name              = ComparisonSymbol.escapedName(renamedMemberPart.getOrElse(resolveResult.name))

        val suffix = resolveResult.element match {
          case _: ScClass | _: ScTypeAlias => "#"
          case _                           => "()."
        }

        forwarderOwnerSymbol + name + suffix
      }
    }


    private def exportOwnerSymbol(exportedIn: PsiElement): Option[String] =
      nearestNamedOwnerSymbol(exportedIn)
        .orElse(topLevelOwnerSymbol(exportedIn.getContainingFile))

    private def nearestNamedOwnerSymbol(element: PsiElement): Option[String] = {
      val selfSymbol = element.asOptionOf[PsiNamedElement]

      val parentSymbol =
        element.contexts
          .takeWhile(!_.is[PsiFile])
          .collectFirst {
            case named: PsiNamedElement => named
          }

      selfSymbol.orElse(parentSymbol).map(ComparisonSymbol.fromPsi)
    }

    private def topLevelOwnerSymbol(file: PsiFile): Option[String] =
      Option(file).map {
        case classOwner: PsiClassOwner if classOwner.getPackageName.nonEmpty =>
          classOwner.getPackageName.split('.').mkString("/") + "/"
        case _ =>
          "_empty_/"
      }
  }

  def disambiguatedStoreFileNameForUppercaseNames(testName: String): Option[String] = {
    if (testName.exists(_.isUpper)) {
      val hash = testName.hashCode.toHexString.take(6)
      Some(s"$testName.$hash.semdb")
    } else {
      None
    }
  }

  def possibleStoreNames(testName: String): Seq[String] = {
    disambiguatedStoreFileNameForUppercaseNames(testName).iterator.toSeq :+ s"$testName.semdb"
  }
}