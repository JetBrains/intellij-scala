package org.jetbrains.plugins.scala
package lang.dependency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.scope.NameHint
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScReferenceExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

/**
 * Pavel Fatin
 */

case class Dependency(kind: DependencyKind, target: PsiElement, path: Path) {
  def isExternal(file: PsiFile, range: TextRange): Boolean = {
    if (ApplicationManager.getApplication.isUnitTestMode) return true

    file != target.getContainingFile || !range.contains(target.getTextRange)
  }
}

object Dependency {
  def dependenciesIn(scope: PsiElement): Seq[Dependency] = {
    scope.depthFirst()
            .filterByType[ScReferenceElement]
            .toList
            .flatMap(reference => dependencyFor(reference).toList)
  }

  def dependencyFor(reference: ScReferenceElement): Option[Dependency] = {
    fastResolve(reference)
      .flatMap(result => dependencyFor(reference, result.element, result.fromType))
  }

  private def fastResolve(ref: ScReferenceElement): Option[ScalaResolveResult] = {
    //we don't want to resolve call reference here for something looking like a named parameter
    ref.contexts.take(3).toSeq match {
      case Seq(ScAssignStmt(`ref`, _), _: ScArgumentExprList, _: MethodInvocation | _: ScSelfInvocation | _: ScConstructor) => return None
      case Seq(ScAssignStmt(`ref`, _), _: ScTuple, _: ScInfixExpr) => return None
      case Seq(ScAssignStmt(`ref`, _), p: ScParenthesisedExpr, inf: ScInfixExpr) if inf.getArgExpr == p => return None
      case _ =>
    }

    implicit val ts = ref.projectContext

    val processor =
      new CompletionProcessor(ref.getKinds(incomplete = false), ref, collectImplicits = false, Some(ref.refName), isIncomplete = false) {
        override def changedLevel: Boolean = {
          val superRes = super.changedLevel

          if (candidatesSet.nonEmpty) false  //stop right away if something was found
          else superRes
        }

        private val nameHint = new NameHint {
          override def getName(state: ResolveState): String = ref.refName
        }

        override def getHint[T](hintKey: Key[T]): T = {
          hintKey match {
            case NameHint.KEY => nameHint.asInstanceOf[T]
            case _ => super.getHint(hintKey)
          }
        }
      }

    val results = ref match {
      case rExpr: ScReferenceExpressionImpl => rExpr.doResolve(processor)
      case stRef: ScStableCodeReferenceElementImpl => stRef.doResolve(processor)
      case _ => Array.empty
    }

    results.collectFirst {
      case srr: ScalaResolveResult => srr
    }
  }

  private def dependencyFor(reference: ScReferenceElement, target: PsiElement, fromType: Option[ScType]): Option[Dependency] = {

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
      val string = path.asString
      val index = string.lastIndexOf('.')
      index == -1 || Set("scala", "java.lang", "scala.Predef").contains(string.substring(0, index))
    }

    def create(entity: PsiNamedElement, member: Option[String] = None): Option[Dependency] = {
      pathFor(entity, member).map(p => Dependency(DependencyKind.Reference, target, p))
    }

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
          case (_: ScPrimaryConstructor) && Parent(e: ScClass) => create(e)
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
          case (function: ScFunctionDefinition) && ContainingClass(obj: ScClass)
            if function.isConstructor =>
            create(obj)
          case (method: PsiMethod) && ContainingClass(e: PsiClass)
            if method.isConstructor =>
            create(e)
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

