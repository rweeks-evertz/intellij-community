// "Round using roundToInt()" "false"
// DISABLE_ERRORS
// ACTION: Change parameter 'x' type of function 'foo' to 'Double'
// ACTION: Convert expression to 'Float'
// ACTION: Create function 'foo'
// WITH_STDLIB
fun test(d: Double) {
    foo(d<caret>)
}

fun foo(x: Float) {}