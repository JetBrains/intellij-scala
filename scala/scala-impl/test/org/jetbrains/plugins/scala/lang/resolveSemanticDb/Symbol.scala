package org.jetbrains.plugins.scala.lang.resolveSemanticDb

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiMethod, PsiNamedElement, PsiPackage, PsiQualifiedNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

import scala.jdk.CollectionConverters.CollectionHasAsScala

private sealed abstract class Symbol extends Product with Serializable

/** Resolves SemanticDB symbols to PSI elements, #SCL-25458
 *
 *  E.g., `example/Foo.bar(+1).` to the second `ScFunction` "bar" in `ScClass` "Foo" in `PsiPackage` "example".
 *
 *  Proof of concept implementation; requires further improvements and refactoring. However, the approach is sound.
 *
 *  Parsing test: [[SymbolTest]]
 *  Resolution test: [[ReferenceComparisonTestBase.assertSymbolResolvesToPSI]], 2736 of 3037 LTS tests pass (90%).
 *
 *  @see [[ComparisonSymbol.fromPsi]], [[ComparisonSymbol.indexOf]]
 */
private object Symbol {
  final case class Package(fqn: String) extends Symbol // fully-qualified package
  final case class Type(name: String) extends Symbol // class, trait, type
  final case class Term(name: String) extends Symbol // object, val
  final case class Method(name: String, n: Int = 0) extends Symbol // method, constructor
  final case class Parameter(name: String) extends Symbol // value parameter (of method or constructor)
  final case class TypeParameter(name: String) extends Symbol // type parameter (of method or class/trait)

  // TODO Handle quotes, e.g. foo/`/`
  private val PackagePattern = "([^.#`]+)/(.*)".r
  private val TypePattern = "([^.]+?)#(.*)".r
  private val TermPattern = "([^#]+?)\\.(.*)".r
  private val MethodPattern = "([^.#]+?)\\((\\+\\d)?\\)\\.(.*)".r
  private val ParameterPattern = "\\((.+?)\\)(.*)".r
  private val TypeParameterPattern = "\\[(.+?)](.*)".r

  def parse(name: String): List[Symbol] = name match {
    case PackagePattern(fqn, tail) => Package(if (fqn == "_empty_") "" else fqn.replace('/', '.')) :: parse(tail)
    case MethodPattern(name, n, tail) => Method(unquoted(name), if (n == null) 0 else n.toInt) :: parse(tail)
    case TypePattern(name, tail) => Type(unquoted(name)) :: parse(tail)
    case TermPattern(name, tail) => Term(unquoted(name)) :: parse(tail)
    case ParameterPattern(name, tail) => Parameter(unquoted(name)) :: parse(tail)
    case TypeParameterPattern(name, tail) => TypeParameter(unquoted(name)) :: parse(tail)
    case "" => Nil
    case s if s.startsWith("local") => Nil
    case s => throw new IllegalArgumentException(s"Cannot parse symbol name: $s")
  }

  private def unquoted(name: String) = {
    if (name.startsWith("`") && name.endsWith("`")) name.substring(1, name.length - 1)
    else name
  }

  // TODO Given, enum, export
  def resolve(parent: Option[PsiNamedElement], path: Seq[Symbol])(implicit project: Project): Either[String, PsiNamedElement] = {
    val manager = ScalaPsiManager.instance(project)
    val scope = GlobalSearchScope.allScope(project)

    path match {
      case Seq(symbol, tail @ _*) => symbol match {
        case Package(fqn) =>
          val pkg = if (fqn.isEmpty) manager.emptyNamePackage else manager.getCachedPackage(fqn)
          pkg.toRight(s"Cannot resolve package '$fqn'").flatMap(p => resolve(Some(p), tail))

        case Type(name) =>
          val tpe = parent match {
            case Some(c: ScTypeDefinition) =>
              (c.typeDefinitions.filter(!_.isObject) ++ c.aliases).find(_.name == name)
            case Some(p: PsiQualifiedNamedElement) =>
              val fqn = if (p.getQualifiedName.isEmpty) name else p.getQualifiedName + "." + name
              manager.getCachedClasses(scope, fqn).find(!_.is[ScObject]).orElse(SyntheticClasses.get(project).aliases.find(_.qualifiedNameOpt.contains(fqn))).orElse {
                parent match {
                  case Some(p: PsiPackage) =>
                    manager.getTopLevelDefinitionsByPackage(p.getQualifiedName, scope).collectFirst { case e: ScTypeAlias if e.name == name => e } orElse {
                      manager.getCachedClasses(scope, p.getQualifiedName).find { case o: ScObject => o.isPackageObject; case _ => false }.flatMap(o => resolve(Some(o), path).toOption)
                    }
                  case _ => None
                }
              }
            case _ => None
          }
          tpe.toRight(s"Cannot resolve type '$name'").flatMap(t => resolve(Some(t), tail))

        case Term(name) if name == "package" || name.endsWith("$package") =>
          resolve(parent, tail)

        case Term(name) =>
          val trm = parent match {
            case Some(c: ScTypeDefinition) => c.allTermsByName(name).headOption
            case Some(p: PsiQualifiedNamedElement) =>
              val fqn = if (p.getQualifiedName.isEmpty) name else p.getQualifiedName + "." + name
              manager.getCachedClasses(scope, fqn).find(_.is[ScObject]).orElse {
                parent match {
                  case Some(p: PsiPackage) =>
                    manager.getTopLevelDefinitionsByPackage(p.getQualifiedName, scope).flatMap { case e: ScValue => e.declaredElements; case _ => Seq.empty }.find(_.name == name) orElse {
                      manager.getCachedClasses(scope, p.getQualifiedName).find { case o: ScObject => o.isPackageObject; case _ => false }.flatMap(o => resolve(Some(o), path).toOption)
                    }
                  case _ => None
                }
              }
            case _ => None
          }
          trm.toRight(s"Cannot resolve term '$name'").flatMap(t => resolve(Some(t), tail))

        case Method(name, n) =>
          val mtd = parent match {
            case Some(c: ScConstructorOwner) =>
              if (name == "<init>") (c.constructor.toSeq ++ c.secondaryConstructors).lift(n)
              else {
                SyntheticClasses.get(project).findClasses(c.qualifiedName).find(!_.is[ScObject]).flatMap(sc => resolve(Some(sc), path).toOption).orElse {
                  (c.parameters.filter(p => p.isValEffectively || p.isVar) ++ c.functions ++ c.syntheticMethods ++ c.extensions.flatMap(_.extensionMethods)).filter(_.name == name).lift(n).orElse {
                    if (name.endsWith("_=")) (c.parameters.filter(_.isVar) ++ c.properties.flatMap { case v: ScVariable => v.declaredElements; case _ => Seq.empty }).filter(_.name == name.dropRight(2)).lift(n)
                    else None
                  }
                }
              }
            case Some(c: ScTypeDefinition) =>
              SyntheticClasses.get(project).findClasses(c.qualifiedName).find(_.is[ScObject]).flatMap(sc => resolve(Some(sc), path).toOption).orElse {
                val patches = if (c.qualifiedName == "scala.Predef") {
                  val mirror = manager.getCachedClasses(scope, "scala.runtime.stdLibPatches.Predef").collectFirst { case o: ScObject => o }
                  mirror.map(c => c.functions ++ c.syntheticMethods ++ c.extensions.flatMap(_.extensionMethods)).getOrElse(Seq.empty)
                } else Seq.empty
                (patches ++ c.functions ++ c.extensions.flatMap(_.extensionMethods)).filter(_.name == name).lift(n).orElse {
                  if (name.endsWith("_=")) c.properties.flatMap { case v: ScVariable => v.declaredElements; case _ => Seq.empty }.filter(_.name == name.dropRight(2)).lift(n)
                  else None
                }
              }
            case Some(c: ScSyntheticClass) =>
              val offset = if (c.qualifiedName == "scala.Int" && name == "+") -1 else 0 // No def +(x: String): String
              c.syntheticMethods.get(name).asScala.toSeq.lift(n + offset).orElse {
                if (c.qualifiedName.startsWith("scala.Any")) manager.getCachedClasses(scope, "java.lang.Object").flatMap(_.findMethodsByName(name, false)).lift(n)
                else None
              }
            case Some(c: PsiClass) =>
              if (c.getQualifiedName == "java.lang.String" && name == "+") Some(SyntheticClasses.get(project).stringPlusMethod(ScDesignatorType(c)))
              else c.findMethodsByName(name, false).lift(n)
            case Some(p: PsiPackage) =>
              manager.getTopLevelDefinitionsByPackage(p.getQualifiedName, scope).collect { case e: ScFunction if e.name == name => e }.toSeq.lift(n) orElse {
                manager.getCachedClasses(scope, p.getQualifiedName).find { case o: ScObject => o.isPackageObject; case _ => false }.flatMap(o => resolve(Some(o), path).toOption)
              }
            case _ => None
          }
          mtd.toRight(s"Cannot resolve method '$name'").flatMap(m => resolve(Some(m), tail))

        case Parameter(name) =>
          val vp = parent match {
            case Some(f: ScFunction) =>
              f.parameters.find(_.name == name).orElse {
                f.extensionMethodOwner.flatMap(_.parameters.find(_.name == name))
              }
            case Some(m: PsiMethod) =>
              m.getParameterList.getParameters.find(_.getName == name)
            case _ => None
          }
          vp.toRight(s"Cannot resolve parameter '$name'").flatMap(p => resolve(Some(p), tail))

        case TypeParameter(name) =>
          val tp = parent match {
            case Some(c: PsiClass) => c.getTypeParameterList.getTypeParameters.find(_.getName == name)
            case Some(m: PsiMethod) => m.getTypeParameterList.getTypeParameters.find(_.getName == name)
            case _ => None
          }
          tp.toRight(s"Cannot resolve type parameter '$name'").flatMap(p => resolve(Some(p), tail))
      }
      case Seq() =>
        parent.toRight("No element for path")
    }
  }
}
