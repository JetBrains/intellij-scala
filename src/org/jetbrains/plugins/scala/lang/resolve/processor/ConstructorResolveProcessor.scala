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
import lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import extensions.toPsiClassExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.04.2010
 */
class ConstructorResolveProcessor(constr: PsiElement, refName: String, args: List[Seq[Expression]],
                                  typeArgs: Seq[ScTypeElement], kinds: Set[ResolveTargets.Value],
                                  shapeResolve: Boolean, allConstructors: Boolean)
        extends MethodResolveProcessor(constr, refName, args, typeArgs, Seq.empty, kinds,
          isShapeResolve = shapeResolve, enableTupling = true) {
  private val qualifiedNames: collection.mutable.HashSet[String] = new collection.mutable.HashSet[String]

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true
      named match {
        case clazz: PsiClass if clazz.qualifiedName != null && !qualifiedNames.contains(clazz.qualifiedName) => {
          qualifiedNames.add(clazz.qualifiedName)
          val constructors: Array[PsiMethod] = 
            if (accessibility) clazz.getConstructors.filter(isAccessible(_, ref))
            else clazz.getConstructors
          if (constructors.isEmpty) {
            //this is for Traits for example. They can be in constructor position.
            // But they haven't constructors.
            addResult(new ScalaResolveResult(clazz, subst, getImports(state), boundClass = getBoundClass(state),
              fromType = getFromType(state), isAccessible = accessible))
          }
          else {
            addResults(constructors.toSeq.map(constr => new ScalaResolveResult(constr, subst, getImports(state),
              parentElement = Some(clazz), boundClass = getBoundClass(state), fromType = getFromType(state),
              isAccessible = isAccessible(constr, ref) && accessible
            )))
          }
        }
        case ta: ScTypeAliasDeclaration => {
          addResult(new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state),
            fromType = getFromType(state), isAccessible = accessible))
        }
        case ta: ScTypeAliasDefinition => {
          lazy val r = new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state),
            fromType = getFromType(state), isAccessible = true)
          val tp = ta.aliasedType(TypingContext.empty).getOrElse({
            addResult(r)
            return true
          })
          ScType.extractClassType(tp) match {
            case Some((clazz, s)) => {
              val constructors: Array[PsiMethod] = 
                if (accessibility) clazz.getConstructors.filter(isAccessible(_, ref))
                else clazz.getConstructors
              if (constructors.isEmpty) addResult(r)
              else {
                addResults(constructors.toSeq.map(constr => new ScalaResolveResult(constr, subst.followed(s), getImports(state),
                    parentElement = Some(ta), boundClass = getBoundClass(state), fromType = getFromType(state),
                    isAccessible = isAccessible(constr, ref) && accessible
                )))
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
    true
  }

  override def candidatesS: Set[ScalaResolveResult] = {
    if (!allConstructors) {
      val superCandidates = super.candidatesS
      if (superCandidates.size <= 1) superCandidates
      else {
        superCandidates.map(constr => new ScalaResolveResult(constr.getActualElement, constr.substitutor,
          constr.importsUsed, boundClass = constr.boundClass, fromType = constr.fromType,
          isAccessible = constr.isAccessible))
      }
    } else {
      super.candidatesS
    }
  }
}