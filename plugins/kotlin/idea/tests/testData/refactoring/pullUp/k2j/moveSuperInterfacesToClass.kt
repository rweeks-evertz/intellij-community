// IGNORE_K2
// TODO: Implement JavaToKotlinPostconversionPullUpHelper
// INFO: {"checked": "true"}
interface X

// INFO: {"checked": "false"}
interface Y

// INFO: {"checked": "true"}
interface Z

class <caret>B: A(), X, Y, Z, J