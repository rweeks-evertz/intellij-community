class Test {

  static class A {
    void foo() {}
    static void foo(A a, A... as) {}
  }

  interface I {
    void bar(A a);
  }

  static void test() {
    I i = A::<error descr="Reference to 'foo' is ambiguous, both 'foo()' and 'foo(A, A...)' match">foo</error>;
  }
}
