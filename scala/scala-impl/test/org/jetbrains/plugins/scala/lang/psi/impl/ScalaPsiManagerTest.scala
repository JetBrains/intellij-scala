package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScExtensionImpl, ScFunctionDefinitionImpl, ScPatternDefinitionImpl, ScTypeAliasDefinitionImpl, ScVariableDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef._
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

/**
 * Related tests:
 *  - [[org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMemberTest]]
 */
class ScalaPsiManagerTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  private val TopLevelDeclarationsOfAllKinds =
    """package org.example
      |
      |class MyClass
      |object MyObject
      |trait MyTrait
      |enum MyEnum { case MyCase }
      |
      |val myVal1 = 1
      |val (myVal2, myVal3) = (2, 3)
      |
      |var myVar1 = 1
      |var (myVar2, myVar3) = (2, 3)
      |
      |def myFunction: String = "42"
      |
      |extension (s: String)
      |  def myExtension: String = s
      |
      |given myGivenAlias: String = "42"
      |given Short = 42
      |given myGivenDefinition: AnyRef with {}
      |
      |type MyAlias = String
      |""".stripMargin

  def testGetClasses_ShouldReturnAllDefinitionsOfTypeDefinitionType(): Unit = {
    configureScala3FromFileText(TopLevelDeclarationsOfAllKinds)

    val manager = ScalaPsiManager.instance(getProject)
    val topLevelDefs = manager.getClasses(manager.getCachedPackage("org.example").get)(GlobalSearchScope.everythingScope(getProject)).toSeq
    val topLevelDefNamesAndClasses: Seq[(String, Class[_ <: PsiElement])] =
      topLevelDefs.sortBy(_.getTextOffset).map {
        case named: ScNamedElement => (named.name, named.getClass)
        case d => (null, d.getClass)
      }

    assertCollectionEquals(
      Seq[(String, Class[_ <: PsiElement])](
        ("MyEnum", classOf[ScObjectImpl]), //synthetic companion of the enum
        ("MyClass", classOf[ScClassImpl]),
        ("MyObject", classOf[ScObjectImpl]),
        ("MyTrait", classOf[ScTraitImpl]),
        ("MyEnum", classOf[ScEnumImpl]),
        ("myGivenDefinition", classOf[ScGivenDefinitionImpl])
      ),
      topLevelDefNamesAndClasses
    )
  }

  def testGetTopLevelDefinitionsByPackage_ShouldReturnAllDefinitionsOfNonTypeDefinitionType(): Unit = {
    configureScala3FromFileText(TopLevelDeclarationsOfAllKinds)

    val manager = ScalaPsiManager.instance(getProject)
    val topLevelDefs = manager.getTopLevelDefinitionsByPackage("org.example", GlobalSearchScope.everythingScope(getProject)).toSeq
    val topLevelDefNamesAndClasses: Seq[(String, Class[_ <: PsiElement])] =
      topLevelDefs.sortBy(_.getTextOffset).map {
        case named: ScNamedElement => (named.name, named.getClass)
        case d => (null, d.getClass)
      }

    assertCollectionEquals(
      Seq[(String, Class[_ <: PsiElement])](
        (null, classOf[ScPatternDefinitionImpl]),
        (null, classOf[ScPatternDefinitionImpl]),
        (null, classOf[ScVariableDefinitionImpl]),
        (null, classOf[ScVariableDefinitionImpl]),
        ("myFunction", classOf[ScFunctionDefinitionImpl[_]]),
        //NOTE: Both extension methods and the containing extension are return
        //The extension is required in usage place to process the exports from its body
        (null, classOf[ScExtensionImpl]),
        ("myExtension", classOf[ScFunctionDefinitionImpl[_]]),
        ("myGivenAlias", classOf[ScGivenAliasDefinitionImpl]),
        ("given_Short", classOf[ScGivenAliasDefinitionImpl]),
        ("MyAlias", classOf[ScTypeAliasDefinitionImpl]),
      ),
      topLevelDefNamesAndClasses
    )
  }
}