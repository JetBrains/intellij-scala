package org.jetbrains.plugins.scala
package lang.dependency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.scope.NameHint
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Pavel Fatin
  */

case class Dependency(target: PsiElement, path: Path)

object Dependency {

  class DependencyProcessor(ref: ScReference) extends CompletionProcessor(ref.getKinds(incomplete = false), ref) {
    override def changedLevel: Boolean = {
      val superRes = super.changedLevel

      if (candidatesSet.nonEmpty) false //stop right away if something was found
      else superRes
    }

    override protected val forName = Some(ref.refName)

    private val nameHint = new NameHint {
      override def getName(state: ResolveState): String = forName.get
    }

    override def getHint[T](hintKey: Key[T]): T = hintKey match {
      case NameHint.KEY => nameHint.asInstanceOf[T]
      case _ => super.getHint(hintKey)
    }
  }

  def dependenciesIn(scope: PsiElement): Seq[Dependency] = scope.depthFirst()
    .instancesOf[ScReference]
    .toList
    .flatMap(dependenciesFor)

  def dependenciesFor(reference: ScReference): List[Dependency] =
    fastResolve(reference).flatMap { result =>
      dependencyFor(reference, result.element, result.fromType)
    }.toList

  def collect(range: TextRange)
             (implicit file: ScalaFile): Iterable[(Path, Seq[ScReference])] = {
    def scopeEstimate(e: PsiElement): Option[PsiElement] =
      e.parentsInFile.flatMap {
        _.prevSiblings
      }.collectFirst {
        case statement: ScImportStmt => statement
        case packaging: ScPackaging => packaging
        case parents: ScTemplateParents => parents
      }

    val groupedReferences = unqualifiedReferencesInRange(range).groupBy { reference =>
      (reference.refName, scopeEstimate(reference), reference.getKinds(incomplete = false))
    }.values

    for {
      references <- groupedReferences
      Dependency(target, path) <- dependenciesFor(references.head)
      if ApplicationManager.getApplication.isUnitTestMode || !isInternal(target, range)
    } yield (path, references)
  }

  private def isInternal(target: PsiElement, range: TextRange)
                        (implicit scalaFile: ScalaFile): Boolean =
    target.getContainingFile match {
      case `scalaFile` => range.contains(target.getTextRange)
      case _ => false
    }

  private def unqualifiedReferencesInRange(range: TextRange)
                                          (implicit file: ScalaFile): Seq[ScReference] =
    file.depthFirst().filter { element =>
      range.contains(element.getTextRange)
    }.collect {
      case ref: ScReferenceExpression if isPrimary(ref) => ref
      case ref: ScStableCodeReferenceImpl if isPrimary(ref) => ref
    }.toSeq

  private def isPrimary(ref: ScReference): Boolean = {
    if (ref.qualifier.nonEmpty) return false

    ref match {
      case _: ScTypeProjection => false
      case ChildOf(sc: ScSugarCallExpr) => ref == sc.getBaseExpr
      case _ => true
    }
  }

  private def fastResolve(ref: ScReference): Option[ScalaResolveResult] = {
    //we don't want to resolve call reference here for something looking like a named parameter
    ref.contexts.take(3).toSeq match {
      case Seq(ScAssignment(`ref`, _), _: ScArgumentExprList, _: MethodInvocation | _: ScSelfInvocation | _: ScConstructor) => return None
      case Seq(ScAssignment(`ref`, _), _: ScTuple, _: ScInfixExpr) => return None
      case Seq(ScAssignment(`ref`, _), p: ScParenthesisedExpr, inf: ScInfixExpr) if inf.argsElement == p => return None
      case _ =>
    }

    implicit val ts: ProjectContext = ref.projectContext
    val processor = new DependencyProcessor(ref)

    val results = ref match {
      case rExpr: ScReferenceExpressionImpl => rExpr.doResolve(processor)
      case stRef: ScStableCodeReferenceImpl => stRef.doResolve(processor)
      case _ => ScalaResolveResult.EMPTY_ARRAY
    }

    results.headOption
  }

  private def dependencyFor(reference: ScReference, target: PsiElement, fromType: Option[ScType]): Option[Dependency] = {

    def pathFor(entity: PsiNamedElement, member: Option[String] = None): Option[Path] = {
      if (!ScalaPsiUtil.hasStablePath(entity)) return None

      val qName = entity match {
        case e: PsiClass => e.qualifiedName
        case e: PsiPackage => e.getQualifiedName
        case _ => return None
      }

      Some(Path(qName, member)).filterNot(shouldSkip)
    }

    def shouldSkip(path: Path): Boolean = {
      val string = path.asString()
      val index = string.lastIndexOf('.')
      index == -1 || Set("scala", "java.lang", "scala.Predef").contains(string.substring(0, index))
    }

    def create(entity: PsiNamedElement, member: Option[String] = None): Option[Dependency] =
      pathFor(entity, member).map(Dependency(target, _))

    reference match {
      case Parent(_: ScConstructorPattern) =>
        val obj = target match {
          case o: ScObject => Some(o)
          case ContainingClass(o: ScObject) => Some(o)
          case _ => None
        }
        obj.flatMap(create(_))
      case _ =>
        target match {
          case _: ScSyntheticClass =>
            None
          case e: PsiClass => create(e)
          case e: PsiPackage => create(e)
          case Constructor.ofClass(c) => create(c)
          case (function: ScFunctionDefinition) && ContainingClass(obj: ScObject)
            if function.isSynthetic || function.name == "apply" || function.name == "unapply" => create(obj)
          case (member: ScMember) && ContainingClass(obj: ScObject) =>
            val memberName = member match {
              case named: ScNamedElement => named.name
              case _ => member.getName
            }
            create(obj, Some(memberName))
          case (pattern: ScReferencePattern) && Parent(Parent(ContainingClass(obj: ScObject))) =>
            create(obj, Some(pattern.name))
          case (method: PsiMember) && ContainingClass(e: PsiClass)
            if method.getModifierList.hasModifierProperty("static") =>
            create(e, Some(method.getName))
          case (member: PsiMember) && ContainingClass(e: PsiClass) =>
            fromType.flatMap(_.extractClass) match {
              case Some(entity: ScObject) =>
                val memberName = member match {
                  case named: ScNamedElement => named.name
                  case _ => member.getName
                }
                create(entity, Some(memberName))
              case _ => None
            }
          case _ => None
        }
    }
  }
}

