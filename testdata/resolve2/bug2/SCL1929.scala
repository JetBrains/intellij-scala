/*resolved: true*/Unit
/*resolved: true*/Boolean
/*resolved: true*/Char
/*resolved: true*/Byte
/*resolved: true*/Short
/*resolved: true*/Int
/*resolved: true*/Long
/*resolved: true*/Float
/*resolved: true*/Double

Char./*resolved: true*/MinValue
Byte./*resolved: true*/MinValue
Short./*resolved: true*/MinValue
Int./*resolved: true*/MinValue
Long./*resolved: true*/MinValue
Float./*resolved: true*/MinValue
Double./*resolved: true*/MinValue

Char./*resolved: true*/MaxValue
Byte./*resolved: true*/MaxValue
Short./*resolved: true*/MaxValue
Int./*resolved: true*/MaxValue
Long./*resolved: true*/MaxValue
Float./*resolved: true*/MaxValue
Double./*resolved: true*/MaxValue

Char./*resolved: true, applicable: true*/box('a')
Byte./*resolved: true*/box(1: Byte)
Short./*resolved: true*/box(1: Short)
Int./*resolved: true*/box(1)
Long./*resolved: true*/box(1L)
Float./*resolved: true*/box(1f)
Double./*resolved: true*/box(1d)

Char./*resolved: true, applicable: true*/unbox(Character.valueOf('a'))
Byte./*resolved: true, applicable: true*/unbox(java.lang.Byte.valueOf(1: Byte))
Short./*resolved: true, applicable: true*/unbox(java.lang.Short.valueOf(1: Short))
Int./*resolved: true, applicable: true*/unbox(java.lang.Integer.valueOf(1))
Long./*resolved: true, applicable: true*/unbox(java.lang.Long.valueOf(1L))
Float./*resolved: true, applicable: true*/unbox(java.lang.Float.valueOf(1f))
Double./*resolved: true, applicable: true*/unbox(java.lang.Double.valueOf(1d))