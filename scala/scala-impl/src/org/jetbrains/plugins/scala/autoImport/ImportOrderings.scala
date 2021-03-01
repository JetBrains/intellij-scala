package org.jetbrains.plugins.scala.autoImport

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocCommentOwner, PsiElement}
import org.jetbrains.plugins.scala.autoImport.quickFix.ElementToImport
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, cachify}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

object ImportOrderings {
  /**
   * Sophisticated (hehe, sure :D) sorting, using all other orderings in a specific order
   * Should be used in all places that have to sort imports
   */
  def defaultImportOrdering(place: PsiElement): Ordering[ElementToImport] = {
    orderingByDeprecated.on[ElementToImport](_.element) orElse
    (
      orderingByImportCountInProject(place) orElse
      orderingByDistanceToLocalImports(place) orElse
      specialPackageOrdering orElse
      orderingByPackageName
    ).on(_.qualifiedName)
  }

  /**
   * Sorts undeprecated elements before deprecated once
   */
  val orderingByDeprecated: Ordering[PsiElement] =
    Ordering.by(_.asOptionOf[PsiDocCommentOwner].exists(_.isDeprecated))

  /**
   * Ordering by natural string ordering of the last part of the import
   *  yyy.A before
   *  xxx.b
   */
  val orderingByPackageName: Ordering[String] = PackageNameOrdering

  /**
   * Ordering by amount of times the import was used in other parts of the project
   */
  def orderingByImportCountInProject(implicit ctx: ProjectContext): Ordering[String] = {
    val cachedQualifierImportCount = cachify(ImportOrderingIndexer.qualifierImportCountF)
    Ordering.by(cachedQualifierImportCount)
      .reverse // most imported should come first
  }

  /**
   * Sorts imports starting with "scala" before those starting with "java" before any other
   */
  val specialPackageOrdering: Ordering[String] = Ordering.by { qual =>
    val idx = qual.indexOf('.')
    if (idx < 0) Int.MaxValue
    else {
      specialPackageWeight.getOrElse(qual.substring(0, idx), Int.MaxValue)
    }
  }

  private val specialPackageWeight: Map[String, Int] = Map(
    "scala" -> 100,
    "java"  -> 1000,
  )

  /**
   * Ordering on qualified names by relevance for the current context.
   *
   * 1. To sort packages, we first get all package qualifier that appear
   *    in import statements that are relevant for `originalRef`.
   *    Additionally we use the qualifier of the current package.
   *    (lets call them context packages)
   *
   * 2. We calculate the minimal distance from a qualified name to all context packages.
   *    For example qualifier `com.libA.blub` has distance of 2 to qualifier `com.libA.blabla`.
   *    Note that two packages qualifiers are not related if they do not share at least the first two package names.
   *
   * 3. If two candidates have the same distance we order them according to their names.
   *    Further, we prefer inner packages to outer packages
   *    (i.e com.a.org.inner.Target should be higher up the list than com.a.Target
   *     iff com.a.org.SomethingElse appears in one of the context imports)
   *
   *    Also we give a little preference to candidates that are near the current package.
   *
   * @param place the place for which the import should be added
   * @return the sorted list of possible imports
   */
  def orderingByDistanceToLocalImports(place: PsiElement): Ordering[String] = {
    val distanceFromContext = cachify(distanceFrom(place))
    Ordering.by(distanceFromContext)
  }

  private def distanceFrom(place: PsiElement): String => Int = {
    val packaging = place.containingScalaFile.flatMap(_.firstPackaging)
    val packageQualifier = packaging.map(_.fullPackageName)
    val ctxImports = getRelevantImports(place)

    val ctxImportRawQualifiers = packageQualifier.toSeq ++
      ctxImports
        .flatMap(_.importExprs)
        .flatMap(e => e.qualifier)
        .map(_.qualName)
    val ctxImportQualifiers = ctxImportRawQualifiers.distinct.map(_.split('.')).toArray

    (fullQualifedName: String) => {
      val candidateQualifier = fullQualifedName.split('.').init
      assert(candidateQualifier.nonEmpty)

      val (dist, prefixLen, bestIdx) = minPackageDistance(candidateQualifier.toSeq, ctxImportQualifiers.toSeq)


      if (prefixLen >= 2) {
        var distanceMod = 0

        // We want inner packages before outer packages
        // base.whereOrgRefWas.inner.Ref
        // base.Ref
        if (bestIdx >= 0 && prefixLen == ctxImportQualifiers(bestIdx).length) {
          distanceMod -= 1
        }

        // if the candidate is nearest to the current package move it further up the import list
        if (packageQualifier.isDefined && bestIdx == 0 && prefixLen >= 2) {
          distanceMod -= 6
        }

        dist * 2 + distanceMod
      } else {
        Int.MaxValue
      }
    }
  }

  // calculates the distance between two package qualifiers
  // two qualifiers that don't share the first two package names are not related at all!
  private def minPackageDistance(qualifier: Seq[String], qualifiers: Seq[Array[String]]): (Int, Int, Int) =
    if (qualifiers.isEmpty) (Int.MaxValue, 0, -1)
    else (for ((t, idx) <- qualifiers.iterator.zipWithIndex) yield {
      val prefixLen = seqCommonPrefixSize(qualifier, t.toSeq)
      val dist =
        if (prefixLen >= 2) qualifier.length + t.length - 2 * prefixLen
        else Int.MaxValue

      (dist, prefixLen, idx)
    }).minBy(_._1)


  private def seqCommonPrefixSize(fst: Seq[String], snd: Seq[String]): Int =
    fst.zip(snd).takeWhile { case (s1, s2) => s1 == s2 }.size

  @tailrec
  private def getRelevantImports(e: PsiElement, foundImports: Seq[ScImportStmt] = Seq.empty): Seq[ScImportStmt] = {
    val found = e match {
      case null => return foundImports
      case holder: ScImportsHolder => foundImports ++ holder.getImportStatements
      case _ => foundImports
    }

    getRelevantImports(e.getParent, found)
  }

  private object PackageNameOrdering extends Ordering[String] {
    private implicit val naturalStringOrdering: Ordering[String] = (x, y) => StringUtil.naturalCompare(x, y)

    override def compare(x: String, y: String): Int = {

      implicitly[Ordering[Tuple2[String, String]]]
        .compare(splitAtLastPoint(x), splitAtLastPoint(y))
    }

    private def splitAtLastPoint(str: String): (String, String) = {
      val i = str.lastIndexOf('.')
      if (i >= 0) (str.substring(0, i), str.substring(i + 1))
      else ("", str)
    }
  }
}
