package org.jetbrains.plugins.scala
package lang.resolve.processor

import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import com.intellij.psi._
import lang.resolve.{ResolveTargets, ScalaResolveResult}
import scala.collection.Set
import lang.psi.api.statements.{ScTypeAliasDefinition, ScTypeAliasDeclaration}
import lang.psi.types.result.TypingContext
import lang.psi.types.ScType
/**
 * User: Alexander Podkhalyuzin
 * Date: 30.04.2010
 */

class ConstructorResolveProcessor(constr: PsiElement, refName: String, args: List[Seq[Expression]],
                                  typeArgs: Seq[ScTypeElement], kinds: Set[ResolveTargets.Value],
                                  shapeResolve: Boolean, allConstructors: Boolean)
        extends MethodResolveProcessor(constr, refName, args, typeArgs, kinds,
          isShapeResolve = shapeResolve, enableTupling = true) {
  private val qualifiedNames: collection.mutable.HashSet[String] = new collection.mutable.HashSet[String]

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      named match {
        case clazz: PsiClass if !qualifiedNames.contains(clazz.getQualifiedName) => {
          qualifiedNames.add(clazz.getQualifiedName)
          val constructors: Array[PsiMethod] = clazz.getConstructors.filter(isAccessible(_, ref))
          if (constructors.isEmpty) {
            //this is for Traits for example. They can be in constructor position.
            // But they haven't constructors.
            addResult(new ScalaResolveResult(clazz, subst, getImports(state), boundClass = getBoundClass(state),
              fromType = getFromType(state)))
          }
          else {
            addResults(constructors.toSeq.map(new ScalaResolveResult(_, subst, getImports(state),
              parentElement = Some(clazz), boundClass = getBoundClass(state), fromType = getFromType(state))))
          }
        }
        case ta: ScTypeAliasDeclaration => {
          addResult(new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state),
            fromType = getFromType(state)))
        }
        case ta: ScTypeAliasDefinition => {
          lazy val r = new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state),
            fromType = getFromType(state))
          val tp = ta.aliasedType(TypingContext.empty).getOrElse({
            addResult(r)
            return true
          })
          ScType.extractClassType(tp) match {
            case Some((clazz, s)) => {
              val constructors: Array[PsiMethod] = clazz.getConstructors.filter(isAccessible(_, ref))
              if (constructors.isEmpty) addResult(r)
              else {
                addResults(constructors.toSeq.map(new ScalaResolveResult(_, subst.followed(s), getImports(state),
                    parentElement = Some(ta), boundClass = getBoundClass(state), fromType = getFromType(state))))
              }
            }
            case _ => {
              addResult(r)
            }
          }
        }
        case _ =>
      }
    }
    return true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    if (!allConstructors) {
      val superCandidates = super.candidatesS
      if (superCandidates.size <= 1) superCandidates
      else {
        superCandidates.map(constr => new ScalaResolveResult(constr.getActualElement, constr.substitutor,
          constr.importsUsed, boundClass = constr.boundClass, fromType = constr.fromType))
      }
    } else {
      super.candidatesS
    }
  }
}