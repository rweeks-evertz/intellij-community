class Foo {
    class Bar
}

fun foo() {
    Foo.Bar().let { bar: Foo.B<caret> -> }
}

// EXIST: Bar