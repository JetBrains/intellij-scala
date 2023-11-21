package org.jetbrains.plugins.scala.lang.parser.scala3

class TypeParserTest extends SimpleScala3ParserTestBase {

  // SCL-21210
  def testHK(): Unit = checkTree(
    """
      |type A = { }#l
      |type B = HKT[{ type l[a] = Option[a] }#l]
      |""".stripMargin,
    """
      |ScalaFile
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: A
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('A')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    TypeProjection: { }#l
      |      CompoundType: { }
      |        Refinement
      |          PsiElement({)('{')
      |          PsiWhiteSpace(' ')
      |          PsiElement(})('}')
      |      PsiElement(#)('#')
      |      PsiElement(identifier)('l')
      |  PsiWhiteSpace('\n')
      |  ScTypeAliasDefinition: B
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(type)('type')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('B')
      |    PsiWhiteSpace(' ')
      |    PsiElement(=)('=')
      |    PsiWhiteSpace(' ')
      |    ParametrizedType: HKT[{ type l[a] = Option[a] }#l]
      |      SimpleType: HKT
      |        CodeReferenceElement: HKT
      |          PsiElement(identifier)('HKT')
      |      TypeArgumentsList
      |        PsiElement([)('[')
      |        TypeProjection: { type l[a] = Option[a] }#l
      |          CompoundType: { type l[a] = Option[a] }
      |            Refinement
      |              PsiElement({)('{')
      |              PsiWhiteSpace(' ')
      |              ScTypeAliasDefinition: l
      |                AnnotationsList
      |                  <empty list>
      |                Modifiers
      |                  <empty list>
      |                PsiElement(type)('type')
      |                PsiWhiteSpace(' ')
      |                PsiElement(identifier)('l')
      |                TypeParameterClause
      |                  PsiElement([)('[')
      |                  TypeParameter: a
      |                    PsiElement(identifier)('a')
      |                  PsiElement(])(']')
      |                PsiWhiteSpace(' ')
      |                PsiElement(=)('=')
      |                PsiWhiteSpace(' ')
      |                ParametrizedType: Option[a]
      |                  SimpleType: Option
      |                    CodeReferenceElement: Option
      |                      PsiElement(identifier)('Option')
      |                  TypeArgumentsList
      |                    PsiElement([)('[')
      |                    SimpleType: a
      |                      CodeReferenceElement: a
      |                        PsiElement(identifier)('a')
      |                    PsiElement(])(']')
      |              PsiWhiteSpace(' ')
      |              PsiElement(})('}')
      |          PsiElement(#)('#')
      |          PsiElement(identifier)('l')
      |        PsiElement(])(']')
      |  PsiWhiteSpace('\n')
      |""".stripMargin
  )
}
