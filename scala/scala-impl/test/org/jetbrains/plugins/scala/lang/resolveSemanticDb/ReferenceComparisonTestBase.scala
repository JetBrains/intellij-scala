package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScMatchTypeElement, ScTypeElement, ScTypeLambdaTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScModifierList, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScQuoted, ScSpliced}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportSelector, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ComparisonTestBase.outPath
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase.RefInfo.{assignmentTarget, opaqueTarget}
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.ReferenceComparisonTestBase._
import org.jetbrains.plugins.scala.util.AliasExports._
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import scala.collection.mutable.ArrayBuffer

abstract class ReferenceComparisonTestBase_Scala3 extends ReferenceComparisonTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0
  // do not spam on in output in failed tests, we have too much of them currently
  override protected def reportFailedTestContextDetails: Boolean = false
}

abstract class ReferenceComparisonTestBase extends ComparisonTestBase {

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

          if (!ref.targets.exists(target => isInRefinement(target.element))) {
            def ignoreSemanticDbRef(ref: SDbRef): Boolean = {
              // ignore locals and implicits involving ClassTag
              ref.pointsToLocal || ref.symbol.contains("ClassTag")
            }

            for(semanticDbRef <- semanticDbReferences if !ignoreSemanticDbRef(semanticDbRef)) {
              didTest = true
              val semanticDbTargetPos = semanticDbRef.targetPosition
              val semanticDbTargetSymbol = ComparisonSymbol.fromSemanticDb(semanticDbRef.symbol)
              val semanticDbTargetSymbol2 = if (!aliasExportsEnabled(getProject)) semanticDbTargetSymbol else {
                val prefix = semanticDbTargetSymbol.stripSuffix("#").stripSuffix(".")
                StandardLibraryAliases.get(prefix).fold(semanticDbTargetSymbol)(semanticDbTargetSymbol.replaceFirst(prefix, _))
              }
              val textFits = ref.targets.exists(_.symbol == semanticDbTargetSymbol2)
              val positionFits = semanticDbTargetPos.exists(ref.targets.map(_.adjustedPosition).contains)

              if (!textFits && !positionFits) {
                val ours = ref.targets
                  .map(target => s"${target.symbol} at ${target.position}")
                  .mkString("\n")
                val semPos = semanticDbTargetPos.fold("<no position>")(_.toString)
                newProblems :+= s"$ref resolves to $semanticDbTargetSymbol2 in semanticdb ($semPos), but we resolve to:\n$ours"
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

    val tags = files.filterByType[ScalaFile].flatMap(collectFeaturesIn).distinct
    Result(problems, refCount, failedToResolve, testedRefs, completeCorrect, partialCorrect, tags)
  }
}

object ReferenceComparisonTestBase {
  // See org.jetbrains.plugins.scala.lang.psi.api.FileDeclarationsHolder
  val StandardLibraryAliases = Map(
    // scala
    "scala/package.Cloneable" -> "java/lang/Cloneable",
    "scala/package.Serializable" -> "java/io/Serializable",

    "scala/package.Throwable" -> "java/lang/Throwable",
    "scala/package.Exception" -> "java/lang/Exception",
    "scala/package.Error" -> "java/lang/Error",

    "scala/package.RuntimeException" -> "java/lang/RuntimeException",
    "scala/package.NullPointerException" -> "java/lang/NullPointerException",
    "scala/package.ClassCastException" -> "java/lang/ClassCastException",
    "scala/package.IndexOutOfBoundsException" -> "java/lang/IndexOutOfBoundsException",
    "scala/package.ArrayIndexOutOfBoundsException" -> "java/lang/ArrayIndexOutOfBoundsException",
    "scala/package.StringIndexOutOfBoundsException" -> "java/lang/StringIndexOutOfBoundsException",
    "scala/package.UnsupportedOperationException" -> "java/lang/UnsupportedOperationException",
    "scala/package.IllegalArgumentException" -> "java/lang/IllegalArgumentException",
    "scala/package.NoSuchElementException" -> "java/util/NoSuchElementException",
    "scala/package.NumberFormatException" -> "java/lang/NumberFormatException",
    "scala/package.AbstractMethodError" -> "java/lang/AbstractMethodError",
    "scala/package.InterruptedException" -> "java/lang/InterruptedException",

    "scala/package.IterableOnce" -> "scala/collection/IterableOnce",
    "scala/package.Iterable" -> "scala/collection/Iterable",
    "scala/package.Seq" -> "scala/collection/immutable/Seq",
    "scala/package.IndexedSeq" -> "scala/collection/immutable/IndexedSeq",
    "scala/package.Iterator" -> "scala/collection/Iterator",
    "scala/package.List" -> "scala/collection/immutable/List",
    "scala/package.Nil" -> "scala/collection/immutable/Nil",
    "scala/package.`::`" -> "scala/collection/immutable/`::`",
    "scala/package.`+:`" -> "scala/collection/`+:`",
    "scala/package.`:+`" -> "scala/collection/`:+`",
    "scala/package.Stream" -> "scala/collection/immutable/Stream",
    "scala/package.LazyList" -> "scala/collection/immutable/LazyList",
    "scala/package.Vector" -> "scala/collection/immutable/Vector",
    "scala/package.StringBuilder" -> "scala/collection/mutable/StringBuilder",
    "scala/package.Range" -> "scala/collection/immutable/Range",

    "scala/package.BigDecimal" -> "scala/math/BigDecimal",
    "scala/package.BigInt" -> "scala/math/BigInt",
    "scala/package.Equiv" -> "scala/math/Equiv",
    "scala/package.Fractional" -> "scala/math/Fractional",
    "scala/package.Integral" -> "scala/math/Integral",
    "scala/package.Numeric" -> "scala/math/Numeric",
    "scala/package.Ordered" -> "scala/math/Ordered",
    "scala/package.Ordering" -> "scala/math/Ordering",

    "scala/package.Either" -> "scala/util/Either",
    "scala/package.Left" -> "scala/util/Left",
    "scala/package.Right" -> "scala/util/Right",

    // scala.Predef
    "scala/Predef.String" -> "java/lang/String",
    "scala/Predef.Class" -> "java/lang/Class",

    "scala/Predef.Map" -> "scala/collection/immutable/Map",
    "scala/Predef.Set" -> "scala/collection/immutable/Set",

    "scala/Predef.OptManifest" -> "scala/reflect/OptManifest",
    "scala/Predef.Manifest" -> "scala/reflect/Manifest",
    "scala/Predef.NoManifest" -> "scala/reflect/NoManifest",
  )

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
      case t: ScTypeElement if t.textMatches("AnyKind") => all += "anykind"
      case p: ScParameter if isByNameImplicit(p)              => all += "byNameImplicit"
      case _: ScEnum                                          => all += "enum"
      case _: ScDerivesClause                                 => all += "derives"
      case _ =>
    }
    all.toSeq
  }

  private def isByNameImplicit(p: ScParameter) = p.isImplicitParameter && p.isCallByNameParameter

  trait RefTarget {
    def element: PsiNamedElement
    lazy val symbol: String = ComparisonSymbol.fromPsi(element)
    def adjustedPosition: TextPos = posOfNavigationElementWithAdjustedEscapeId(element)
    def position: TextPos = TextPos.of(element.getNavigationElement)
  }

  case class PhysicalRefTarget(element: PsiNamedElement) extends RefTarget

  case class DesugaredEnumRefTarget(element: PsiNamedElement) extends RefTarget

  private def desugaredEnumTarget(syntheticElement: PsiNamedElement): Option[RefTarget] =
    syntheticElement match {
      case ScEnum.Original(enum)    => Option(DesugaredEnumRefTarget(enum))
      case ScEnumCase.Original(cse) => Option(DesugaredEnumRefTarget(cse))
      case _                        => None
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
      .flatMap { named =>
        Seq(PhysicalRefTarget(named)) ++ assignmentTarget(named) ++ opaqueTarget(named) ++ desugaredEnumTarget(named)
      }

    /*lazy val problems: Option[String] = {
      val resultsWithProblems = resolved.filter(_.problems.nonEmpty)
      if (resultsWithProblems.nonEmpty && resultsWithProblems.sizeIs == resolved.size)
        Some(resultsWithProblems.map(rr => rr.problems.mkString(" and ") + s" for ${rr.name}").mkString(", "))
      else None
    }*/
    def failedToResolve: Boolean = resolved.size != 1 || problems.nonEmpty
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
        isImplicit = false
      )
    }

    def forImplicitArguments(iao: ImplicitArgumentsOwner): Seq[RefInfo] = {
      iao.findImplicitArguments match {
        case Some(iargs) =>
          iargs.zipWithIndex.flatMap { case (rr, i) =>
            val file = iao.getContainingFile
            val problems = rr.problems match {
              case Seq() => None
              case problems => Some(problems.mkString(", "))
            }
            Some(RefInfo(
              s"implicit-param:$i",
              TextPos.at(iao.endOffset, file),
              Seq(rr),
              file.name,
              problems,
              isImplicit = true
            ))
          }
        case None =>
          Seq.empty
      }
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

    private def opaqueTarget(resolved: PsiNamedElement): Option[PhysicalRefTarget] = resolved match {
      case typeDef: ScTypeAliasDefinition if !typeDef.hasModifierPropertyScala("opaque") =>
        val aliased = typeDef.aliasedType.toOption.flatMap(_.extractClass)
        aliased.map(PhysicalRefTarget)
      case _ => None
    }
  }
}