// "Change to '1'" "false"
// ACTION: Convert to lazy property
// ACTION: Change type of 'a' to 'Double'
// ACTION: Convert expression to 'Int'
// ACTION: Convert property initializer to getter
// ACTION: Round using roundToInt()
// ERROR: The floating-point literal does not conform to the expected type Int
// K2_AFTER_ERROR: Initializer type mismatch: expected 'Int', actual 'Double'.

val a : Int = 1.12<caret>