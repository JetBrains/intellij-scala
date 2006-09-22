package org.jetbrains.plugins.scala.lang.lexer;

%%
%class Lexer
%unicode
%public

//user code//


//////////////////  reserved words  //////////////////////////////////////////////////////////////////////////////////////

KEYWORDS  = "abstract" | "case"    | "catch"     | "class"      | "def"
            "do"       | "else     | "extends"   | "false"      | "final"
            "finally"  | "for"     | "if"        | "implicit"   | "import"
            "match"    | "new"     | "null"      | "object"     | "override"
            "package"  | "private" | "protected" | "requires"   | "return"
            "sealed"   | "super"   | "this"      | "throw"      | "trait"
            "try"      | "true"    | "type"      | "val"        | "var"
            "while"    | "with"    | "yield"

            
UNICODEESCAPE = \{\\}u{u} HEXDIGIT HEXDIGIT HEXDIGIT HEXDIGIT

HEXDIGIT = '0' | . . . | '9' | 'A' | . . . | 'F' | 'a' | . . . | 'f' |

WHITESPACESCHAR = \u0020 | \u0009 | \u000D | \u000A
WHITESPACES = {WHITESPACESCHAR}+

characterLiteral = '\'' char '\''
| '\'' charEscapeSeq '\''

stringLiteral = '\"' {stringElement} '\"'
stringElement = charNoDoubleQuote | charEscapeSeq



upper = 'A' | . . . | 'Z' | '$' | '_' and Unicode Lu
lower = 'a' | . . . | 'z' and Unicode Ll
letter = upper | lower and Unicode categories Lo, Lt, Nl
digit = '0' | . . . | '9'
special = “all other characters in\u0020-007F and Unicode categories

Sm, So except parentheses ([]) and periods”
op = special {special}
varid = lower idrest
plainid = upper idrest
    | varid
    | op

id = plainid
| '\'' stringLit '\''

idrest = {letter | digit} ['_' op]

/////////////////////      integers      /////////////////////////////////////////////////////////////////////////////////////////

integerLiteral = (decimalNumeral | hexNumeral | octalNumeral) ['L' | 'l']
decimalNumeral = '0' | nonZeroDigit {digit}
hexNumeral = '0' 'x' hexDigit {hexDigit}
octalNumeral = '0' octalDigit {octalDigit}
digit = '0' | nonZeroDigit
nonZeroDigit = '1' | . . . | '9'
octalDigit = '0' | . . . | '7'


////////////////////  floating point  ////////////////////////////////////////////////////////////////////////////////////

floatingPointLiteral = digit {digit} '.' {digit} [exponentPart] [floatType]
                        | '.' digit {digit} [exponentPart] [floatType]

                        | digit {digit} exponentPart [floatType]
                        | digit {digit} [exponentPart] floatType
exponentPart = ('E' | 'e') ['+' | '']
digit {digit}
floatType = 'F' | 'f' | 'D' | 'd'

booleanLiteral = true | false

characterLiteral= '\'' char '\''
| '\'' charEscapeSeq '\''

stringLiteral = '"' {stringElement} '"'
stringElement = charNoDoubleQuote
                | charEscapeSeq
symbolLiteral = ''' plainid
comment = '/*' ''any sequence of characters'' '*/'
            | '//' 'any sequence of characters up to end of line''


Literal = integerLiteral
            | floatingPointLiteral
            | booleanLiteral
            | characterLiteral
            | stringLiteral
            | symbolLiteral
            | null

StableId = id
            | Path '.' id
Path = StableId
        | [id '.'] this
        | [id '.'] super ['[' id ']']'.' id

Type = Type1 '=>' Type
| '(' [Types] ')' '=>' Type
| Type1
Type1 = SimpleType {with SimpleType} [Refinement]
SimpleType = SimpleType TypeArgs
| SimpleType '#' id
| StableId
| Path '.' type
| '(' Type ')'
TypeArgs = '[' Types ']'
Types = Type {',' Type}


Refinement = '{' [RefineStat {StatementSeparator RefineStat}] '}'
RefineStat = Dcl
| type TypeDef
|

Exprs = Expr {',' Expr} [':' '_' '*']
Expr = (Bindings | Id) '=>' Expr
| Expr1

Expr1 = if '(' Expr1 ')' [NewLine] Expr [[';'] else Expr]
| try '{' Block '}' [catch '{' CaseClauses '}'] [finally Expr]
| while '(' Expr ')' [NewLine] Expr
| do Expr [StatementSeparator] while '(' Expr ')'
| for ('(' Enumerators ')' | '{' Enumerators '}') [NewLine] [yield] Expr
| throw Expr
| return [Expr]
| [SimpleExpr '.'] id '=' Expr
| SimpleExpr ArgumentExprs '=' Expr
| PostfixExpr [':' Type1]
| PostfixExpr match '{' CaseClauses '}'
| MethodClosure

PostfixExpr = InfixExpr [id [NewLine]]

InfixExpr = PrefixExpr
| InfixExpr id [NewLine] PrefixExpr

PrefixExpr = [''
| '+' | '~' | '!' | '&'] SimpleExpr

SimpleExpr = Literal
| Path
| '(' [Expr] ')'
| BlockExpr
| new Template
| SimpleExpr '.' id
| SimpleExpr TypeArgs
| SimpleExpr ArgumentExprs
| XmlExpr

ArgumentExprs = '(' [Exprs] ')'
| BlockExpr

MethodClosure = '.' Id {'.' Id | TypeArgs | ArgumentExprs}

BlockExpr = '{' CaseClauses '}' | '{' Block '}'
Block = {BlockStat StatementSeparator} [ResultExpr]

BlockStat = Import
| [implicit] Def
| {LocalModifier} TmplDef
| Expr1
|

ResultExpr = Expr1
| (Bindings | Id [':' Type1]) '=>' Block

Enumerators = Generator {StatementSeparator Enumerator}
Enumerator = Generator
| val Pattern1 '=' Expr
| Expr

Generator = val Pattern1 '<'
Expr

CaseClauses = CaseClause { CaseClause }
CaseClause = case Pattern ['if' PostfixExpr] '=>' Block
Constr = StableId [TypeArgs] {'(' [Exprs] ')'}
Pattern = Pattern1 { '|' Pattern1 }
Pattern1 = varid ':' Type
| '_' ':' Type
| Pattern2

Pattern2 = varid ['@' Pattern3]
| Pattern3

Pattern3 = SimplePattern [ '*' | '?' | '+' ]
| SimplePattern { id SimplePattern }

SimplePattern = '_'
| varid
| Literal
| StableId [ '(' [Patterns] ')' ]
| '(' [Patterns] ')'
| XmlPattern

Patterns = Pattern {',' Pattern}
TypeParamClause = [NewLine] '[' VariantTypeParam {',' VariantTypeParam} ']'
FunTypeParamClause=[NewLine] '[' TypeParam {',' TypeParam} ']'
VariantTypeParam= ['+' | '']
TypeParam
TypeParam = id [>: Type] [<: Type] [<% Type]
ParamClauses = {ParamClause} [[NewLine] '(' implicit Params ')']
ParamClause = [NewLine] '(' [Params] ')'}
Params = Param {',' Param}
Param = id ':' ParamType
ParamType = ['=>'] Type ['*']
ClassParamClauses= {ClassParamClause} [[NewLine]
'(' implicit ClassParams ')']
ClassParamClause= [NewLine] '(' [ClassParams ')'
ClassParams = ClassParam {'' ClassParam}
ClassParam = [{Modifier} ('val' | 'var')] Param
Bindings = '(' Binding {',' Binding ')'
Binding = id [':' Type]


Modifier = LocalModifier
| override
| private [ "[" id "]" ]
| protected [ "[" id "]" ]

LocalModifier = abstract
| final
| sealed
| implicit

AttributeClause = '[' Attribute {',' Attribute} ']' [NewLine]
Attribute = Constr
Template = TemplateParents [TemplateBody]
TemplateParents = Constr {with SimpleType}
TemplateBody = '{' [TemplateStat {StatementSeparator TemplateStat}] '}'
TemplateStat = Import
| {AttributeClause} {Modifier} Def
| {AttributeClause} {Modifier} Dcl
| Expr
|

Import = import ImportExpr {',' ImportExpr}
ImportExpr = StableId '.' (id | '_' | ImportSelectors)
ImportSelectors = '{' {ImportSelector ','}
(ImportSelector | '_') '}'
ImportSelector = id ['=>' id | '=>' '_']

Dcl = val ValDcl
| var VarDcl
| def FunDcl
| type TypeDcl
ValDcl = ids ':' Type
VarDcl = ids ':' Type
FunDcl = FunSig ':' Type
FunSig = id [FunTypeParamClause] {ParamClause}
TypeDcl = id [>: Type] [<: Type]
Def = val PatDef
| var VarDef
| def FunDef
| type TypeDef
| TmplDef

PatDef = Pattern2 {',' Pattern2} [':' Type] '=' Expr
VarDef = ids [':' Type] '=' Expr


| ids ':' Type '=' '_'
FunDef = FunSig ':' Type '=' Expr
| this ParamClause ParamClauses '=' ConstrExpr

TypeDef = id [TypeParamClause] '=' Type
TmplDef = ([case] class ClassDef
| [case] object ObjectDef
| trait TraitDef
ClassDef = id [TypeParamClause] ClassParamClauses
['requires' SimpleType] ClassTemplate
ClassTemplate = [extends TemplateParents] [[NewLine] TemplateBody]
TraitDef = id [TypeParamClause]
['requires' SimpleType] TraitTemplate
TraitTemplate = [extends MixinParents] [[NewLine] TemplateBody]
MixinParents = SimpleType {'with' SimpleType}
ObjectDef = id ClassTemplate
ConstrExpr = SelfInvocation | '{' SelfInvocation {StatementSeparator BlockStat} '}'
SelfInvocation = this ArgumentExprs {ArgumentExprs}
CompilationUnit = [package QualId StatementSeparator] TopStatSeq
TopStatSeq = TopStat {StatementSeparator TopStat}
TopStat = {AttributeClause} {Modifier} TmplDef
| Import
| Packaging
|

Packaging = package QualId '{' TopStatSeq '}'
QualId = id {'.' id}
ids = id {',' id}