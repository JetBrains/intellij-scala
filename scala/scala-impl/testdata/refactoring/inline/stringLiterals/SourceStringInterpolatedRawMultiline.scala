val /*caret*/value =raw"""injected text raw \\s"""
s"""outer text \\s ${2 + 2} $value"""
/*
s"""outer text \\s ${2 + 2} injected text raw \\\\s"""
 */
