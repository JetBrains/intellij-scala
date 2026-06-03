val /*caret*/value = s"""injected text raw \\s"""
raw"""outer text \\s ${2 + 2} $value"""
/*
raw"""outer text \\s ${2 + 2} injected text raw \s"""
 */
