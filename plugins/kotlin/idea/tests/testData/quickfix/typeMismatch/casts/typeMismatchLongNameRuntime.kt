// "Cast expression 'module' to 'LinkedHashSet<Int>'" "true"
// DISABLE_ERRORS

fun foo(): java.util.LinkedHashSet<Int> {
    val module: java.util.HashSet<Int> = java.util.LinkedHashSet<Int>()
    return module<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CastExpressionFixFactories$CastExpressionModCommandAction