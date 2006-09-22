package org.jetbrains.plugins.scala.lang.lexer;

%%
%class Lexer
%unicode
%public

//user code//

/////////////////////      integers      /////////////////////////////////////////////////////////////////////////////////////////

INTEGERLITERAL = (decimalNumeral | hexNumeral | octalNumeral) ['L' | 'l']
decimalNumeral = '0' | nonZeroDigit {digit}
hexNumeral = '0' 'x' hexDigit {hexDigit}
octalNumeral = '0' octalDigit {octalDigit}
digit = '0' | nonZeroDigit
nonZeroDigit = '1' | . . . | '9'
octalDigit = '0' | . . . | '7'




////////////////////  floating point  ////////////////////////////////////////////////////////////////////////////////////

floatingPointLiteral ::= digit {digit} ‘.’ {digit} [exponentPart] [floatType]
| ‘.’ digit {digit} [exponentPart] [floatType]
| digit {digit} exponentPart [floatType]
| digit {digit} [exponentPart] floatType
exponentPart ::= (’E’ | ’e’) [’+’ | ’’]
digit {digit}
floatType ::= ’F’ | ’f’ | ’D’ | ’d’


//////////////////  reserved words  //////////////////////////////////////////////////////////////////////////////////////

KEYWORDS  = "abstract" | "case"    | "catch"     | "class"      | "def"
            "do"       | "else     | "extends"   | "false"      | "final"
            "finally"  | "for"     | "if"        | "implicit"   | "import"
            "match"    | "new"     | "null"      | "object"     | "override"
            "package"  | "private" | "protected" | "requires"   | "return"
            "sealed"   | "super"   | "this"      | "throw"      | "trait"
            "try"      | "true"    | "type"      | "val"        | "var"
            "while"    | "with"    | "yield"

            
UNICODEESCAPE = \{\\}u{u} hexDigit hexDigit hexDigit hexDigit

HEXDIGIT = ’0’ | . . . | ‘9’ | ‘A’ | . . . | ‘F’ | ‘a’ | . . . | ‘f’ |

WHITESPACES = \u0020 | \u0009 | \u000D | \u000A