val /*caret*/value = s"injected text interpolated \\s"
s"""outer text \\s ${2 + 2} $value"""
/*
s"""outer text \\s ${2 + 2} injected text interpolated \\s"""
 */
