package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

class SAMInheritorsSearcher
    extends QueryExecutorBase[PsiElement, DefinitionsScopedSearch.SearchParameters](true)
    with UsageToPsiElements {
  import SAMInheritorsSearcher._

  override def processQuery(
    params:    DefinitionsScopedSearch.SearchParameters,
    processor: Processor[_ >: PsiElement]
  ): Unit = params.getElement match {
    case aClass: PsiClass =>
      val project = aClass.getProject
      val service = ScalaCompilerReferenceService.getInstance(project)
      if (service.isCompilerIndexReady) {
        val usages = service.SAMInheritorsOf(aClass)
        getSAMInheritors(usages.unwrap, aClass, project).foreach(processor.process)
      }
    case _ => ()
  }

  private[this] def getSAMInheritors(usages: Set[UsagesInFile], target: PsiClass, project: Project): Seq[PsiElement] =
    usages
      .flatMap(extractCandidatesFromUsage(project, _))
      .flatMap(_.elements)
      .collect {
        case e @ SAMMatcher(`target`) => e
      }(collection.breakOut)
}

object SAMInheritorsSearcher {
  private val SAMMatcher: SAMInheritorMatcher = new SAMInheritorMatcher(checkValidity = false)

  private object HasExpectedClassType {
    def unapply(e: ScExpression): Option[(ScType, PsiClass)] =
      for {
        tpe    <- e.expectedType()
        aClass <- tpe.extractClass
      } yield (tpe, aClass)
  }

  class SAMInheritorMatcher(checkValidity: Boolean = true) {
    def isValidSAM(e: ScExpression, samTpe: ScType): Boolean =
      if (checkValidity) ScalaPsiUtil.toSAMType(samTpe, e).isDefined
      else               true

    def unapply(e: ScExpression): Option[PsiClass] = e match {
      case (_: ScFunctionExpr) && HasExpectedClassType(tpe, cls) if isValidSAM(e, tpe)        => Option(cls)
      case ScBlock(_: ScFunctionExpr) && HasExpectedClassType(tpe, cls) if isValidSAM(e, tpe) => Option(cls)
      case MethodValue(_) && HasExpectedClassType(tpe, cls) if isValidSAM(e, tpe)             => Option(cls)
      case _                                                                                  => None
    }
  }
}
