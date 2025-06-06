// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Strings {
  private static final List<String> REPLACES_REFS = Arrays.asList("&lt;", "&gt;", "&amp;", "&#39;", "&quot;");
  private static final List<String> REPLACES_DISP = Arrays.asList("<", ">", "&", "'", "\"");

  public static final CharSequence EMPTY_CHAR_SEQUENCE = new CharArrayCharSequence(ArrayUtilRt.EMPTY_CHAR_ARRAY);

  public static boolean isAscii(char ch) {
    return ch < 128;
  }

  @Contract(pure = true)
  public static boolean isDecimalDigit(char c) {
    return c >= '0' && c <= '9';
  }

  @Contract("null -> false")
  public static boolean isNotNegativeNumber(@Nullable CharSequence s) {
    if (s == null) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      if (!isDecimalDigit(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Contract(pure = true)
  public static int compare(char c1, char c2, boolean ignoreCase) {
    // duplicating String.equalsIgnoreCase logic
    int d = c1 - c2;
    if (d == 0 || !ignoreCase) {
      return d;
    }
    // If characters don't match but case may be ignored,
    // try converting both characters to uppercase.
    // If the results match, then the comparison scan should
    // continue.
    char u1 = StringUtilRt.toUpperCase(c1);
    char u2 = StringUtilRt.toUpperCase(c2);
    d = u1 - u2;
    if (d != 0) {
      // Unfortunately, conversion to uppercase does not work properly
      // for the Georgian alphabet, which has strange rules about case
      // conversion.  So we need to make one last check before
      // exiting.
      d = StringUtilRt.toLowerCase(u1) - StringUtilRt.toLowerCase(u2);
    }
    return d;
  }

  @Contract(pure = true)
  public static int compare(@Nullable CharSequence s1, @Nullable CharSequence s2, boolean ignoreCase) {
    if (s1 == s2) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return 1;

    int length1 = s1.length();
    int length2 = s2.length();
    int i = 0;
    for (; i < length1 && i < length2; i++) {
      int diff = compare(s1.charAt(i), s2.charAt(i), ignoreCase);
      if (diff != 0) {
        return diff;
      }
    }
    return length1 - length2;
  }

  @Contract(pure = true)
  public static boolean charsMatch(char c1, char c2, boolean ignoreCase) {
    return compare(c1, c2, ignoreCase) == 0;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toLowerCase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ENGLISH);
  }

  @Contract(pure = true)
  public static char toLowerCase(final char a) {
    return StringUtilRt.toLowerCase(a);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String toUpperCase(String s) {
    return s == null ? null : s.toUpperCase(Locale.ENGLISH);
  }

  @Contract(pure = true)
  public static char toUpperCase(char a) {
    return StringUtilRt.toUpperCase(a);
  }

  @Contract(pure = true)
  public static boolean contains(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return indexOf(sequence, infix) >= 0;
  }

  /**
   * Allows to answer if target symbol is contained at given char sequence at {@code [start; end)} interval.
   *
   * @param s     target char sequence to check
   * @param start start offset to use within the given char sequence (inclusive)
   * @param end   end offset to use within the given char sequence (exclusive)
   * @param c     target symbol to check
   * @return {@code true} if given symbol is contained at the target range of the given char sequence;
   * {@code false} otherwise
   */
  @Contract(pure = true)
  public static boolean contains(@NotNull CharSequence s, int start, int end, char c) {
    return indexOf(s, c, start, end) >= 0;
  }


  @Contract(pure = true)
  public static boolean containsChar(final @NotNull String value, final char ch) {
    return value.indexOf(ch) >= 0;
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(final @NotNull String value, final @NotNull String chars) {
    return chars.length() > value.length()
           ? containsAnyChar(value, chars, 0, value.length())
           : containsAnyChar(chars, value, 0, chars.length());
  }

  @Contract(pure = true)
  public static boolean containsAnyChar(final @NotNull String value,
                                        final @NotNull String chars,
                                        final int start, final int end) {
    for (int i = start; i < end; i++) {
      if (chars.indexOf(value.charAt(i)) >= 0) {
        return true;
      }
    }

    return false;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c) {
    return indexOf(s, c, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start) {
    return indexOf(s, c, start, s.length());
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start, int end) {
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (s.charAt(i) == c) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix) {
    return indexOf(sequence, infix, 0);
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix, int start) {
    return indexOf(sequence, infix, start, sequence.length());
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence sequence, @NotNull CharSequence infix, int start, int end) {
    for (int i = start; i <= end - infix.length(); i++) {
      if (startsWith(sequence, i, infix)) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOf(@NotNull CharSequence s, char c, int start, int end, boolean caseSensitive) {
    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (charsMatch(s.charAt(i), c, !caseSensitive)) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOf(char @NotNull [] s, char c, int start, int end, boolean caseSensitive) {
    end = Math.min(end, s.length);
    for (int i = Math.max(start, 0); i < end; i++) {
      boolean ignoreCase = !caseSensitive;
      if (charsMatch(s[i], c, ignoreCase)) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull String s, final @NotNull String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull CharSequence s, final @NotNull String chars) {
    return indexOfAny(s, chars, 0, s.length());
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull String s, final @NotNull String chars, final int start, final int end) {
    return indexOfAny((CharSequence)s, chars, start, end);
  }

  @Contract(pure = true)
  public static int indexOfAny(final @NotNull CharSequence s, final @NotNull String chars, final int start, int end) {
    if (chars.isEmpty()) return -1;

    end = Math.min(end, s.length());
    for (int i = Math.max(start, 0); i < end; i++) {
      if (containsChar(chars, s.charAt(i))) return i;
    }
    return -1;
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull String where, @NotNull String what, int fromIndex) {
    return indexOfIgnoreCase((CharSequence)where, what, fromIndex);
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull CharSequence where,
                                      @NotNull CharSequence what,
                                      int start) {
    return indexOfIgnoreCase(where, what, start, where.length());
  }

  /**
   * Implementation copied from {@link String#indexOf(String, int)} except character comparisons made case-insensitive
   */
  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull CharSequence where,
                                      @NotNull CharSequence what,
                                      int startOffset,
                                      int endOffset) {
    if (endOffset < startOffset) {
      return -1;
    }

    int targetCount = what.length();
    int sourceCount = where.length();

    if (startOffset >= sourceCount) {
      return targetCount == 0 ? sourceCount : -1;
    }

    if (startOffset < 0) {
      startOffset = 0;
    }

    if (targetCount == 0) {
      return startOffset;
    }

    char first = what.charAt(0);
    int max = endOffset - targetCount;

    for (int i = startOffset; i <= max; i++) {
      /* Look for first character. */
      if (!charsEqualIgnoreCase(where.charAt(i), first)) {
        //noinspection StatementWithEmptyBody,AssignmentToForLoopParameter
        while (++i <= max && !charsEqualIgnoreCase(where.charAt(i), first)) ;
      }

      /* Found first character, now look at the rest of v2 */
      if (i <= max) {
        int j = i + 1;
        int end = j + targetCount - 1;
        //noinspection StatementWithEmptyBody
        for (int k = 1; j < end && charsEqualIgnoreCase(where.charAt(j), what.charAt(k)); j++, k++) ;

        if (j == end) {
          /* Found whole string. */
          return i;
        }
      }
    }

    return -1;
  }

  @Contract(pure = true)
  public static int indexOfIgnoreCase(@NotNull String where, char what, int fromIndex) {
    int sourceCount = where.length();
    for (int i = Math.max(fromIndex, 0); i < sourceCount; i++) {
      if (charsEqualIgnoreCase(where.charAt(i), what)) {
        return i;
      }
    }

    return -1;
  }

  @Contract(pure = true)
  public static boolean charsEqualIgnoreCase(char a, char b) {
    return charsMatch(a, b, true);
  }

  /**
   * Capitalize the first letter of the sentence.
   */
  @Contract(pure = true)
  public static @NotNull String capitalize(@NotNull String s) {
    if (s.isEmpty()) return s;
    if (s.length() == 1) return toUpperCase(s);
    if (Character.isUpperCase(s.charAt(0))) return s;
    return toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isCapitalized(@Nullable String s) {
    return s != null && !s.isEmpty() && Character.isUpperCase(s.charAt(0));
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull CharSequence text, int startIndex, @NotNull CharSequence prefix) {
    int tl = text.length();
    if (startIndex < 0 || startIndex > tl) {
      throw new IllegalArgumentException("Index is out of bounds: " + startIndex + ", length: " + tl);
    }
    int l1 = tl - startIndex;
    int l2 = prefix.length();
    if (l1 < l2) return false;

    for (int i = 0; i < l2; i++) {
      if (text.charAt(i + startIndex) != prefix.charAt(i)) return false;
    }
    return true;
  }

  @Contract(pure = true)
  public static boolean endsWith(@NotNull CharSequence text, @NotNull CharSequence suffix) {
    return StringUtilRt.endsWith(text, suffix);
  }

  @Contract(pure = true)
  public static boolean endsWithChar(@Nullable CharSequence s, char suffix) {
    return StringUtilRt.endsWithChar(s, suffix);
  }

  @Contract(pure = true)
  public static boolean endsWithIgnoreCase(@NotNull CharSequence str, @NotNull String suffix) {
    return StringUtilRt.endsWithIgnoreCase(str, suffix);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isNotEmpty(@Nullable String s) {
    return !isEmpty(s);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable CharSequence cs) {
    return StringUtilRt.isEmpty(cs);
  }

  @Contract(pure = true)
  public static @NotNull String pluralize(@NotNull String word) {
    String plural = Pluralizer.PLURALIZER.plural(word);
    if (plural != null) return plural;
    if (word.endsWith("s")) return Pluralizer.restoreCase(word, word + "es");
    return Pluralizer.restoreCase(word, word + "s");
  }

  /**
   * Returns unpluralized variant using English based heuristics like properties -> property, names -> name, children -> child.
   * Returns {@code null} if failed to match appropriate heuristic.
   *
   * @param word english word in plural form
   * @return name in singular form or {@code null} if failed to find one.
   */
  @Contract(pure = true)
  public static @Nullable String unpluralize(@NotNull String word) {
    String singular = Pluralizer.PLURALIZER.singular(word);
    if (singular != null) return singular;
    if (word.endsWith("es")) return nullize(trimEnd(word, "es", true));
    if (word.endsWith("s")) return nullize(trimEnd(word, "s", true));
    return null;
  }

  @Contract(pure = true)
  public static @NotNull String notNullize(@Nullable String s) {
    return StringUtilRt.notNullize(s);
  }

  @Contract(pure = true)
  public static @NotNull String notNullize(@Nullable String s, @NotNull String defaultValue) {
    return StringUtilRt.notNullize(s, defaultValue);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s) {
    return nullize(s, false);
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s, @Nullable String defaultValue) {
    boolean empty = isEmpty(s) || Objects.equals(s, defaultValue);
    return empty ? null : s;
  }

  @Contract(pure = true)
  public static @Nullable String nullize(@Nullable String s, boolean nullizeSpaces) {
    boolean empty = nullizeSpaces ? isEmptyOrSpaces(s) : isEmpty(s);
    return empty ? null : s;
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmptyOrSpaces(@Nullable CharSequence s) {
    return StringUtilRt.isEmptyOrSpaces(s);
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static String trim(@Nullable String s) {
    return s == null ? null : s.trim();
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, @NotNull String suffix) {
    return trimEnd(s, suffix, false);
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, @NotNull String suffix, boolean ignoreCase) {
    boolean endsWith = ignoreCase ? endsWithIgnoreCase(s, suffix) : s.endsWith(suffix);
    if (endsWith) {
      return s.substring(0, s.length() - suffix.length());
    }
    return s;
  }

  @Contract(pure = true)
  public static @NotNull String trimEnd(@NotNull String s, char suffix) {
    if (endsWithChar(s, suffix)) {
      return s.substring(0, s.length() - 1);
    }
    return s;
  }

  @Contract(pure = true)
  public static @NotNull String trimStart(@NotNull String s, @NotNull String prefix) {
    if (s.startsWith(prefix)) {
      return s.substring(prefix.length());
    }
    return s;
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars) {
    return StringsKmp.stringHashCode(chars);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to) {
    return stringHashCode(chars, from, to, 0);
  }

  @Contract(pure = true)
  public static int stringHashCode(@NotNull CharSequence chars, int from, int to, int prefixHash) {
    return StringsKmp.stringHashCode(chars, from, to, prefixHash);
  }

  @Contract(pure = true)
  public static int stringHashCode(char @NotNull [] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + chars[off];
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(char @NotNull [] chars, int from, int to) {
    int h = 0;
    for (int off = from; off < to; off++) {
      h = 31 * h + toLowerCase(chars[off]);
    }
    return h;
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to) {
    return StringUtilRt.stringHashCodeInsensitive(chars, from, to);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars, int from, int to, int prefixHash) {
    return StringUtilRt.stringHashCodeInsensitive(chars, from, to, prefixHash);
  }

  @Contract(pure = true)
  public static int stringHashCodeInsensitive(@NotNull CharSequence chars) {
    return StringUtilRt.stringHashCodeInsensitive(chars);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c) {
    return countChars(text, c, 0, false);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c, int offset, boolean stopAtOtherChar) {
    return countChars(text, c, offset, text.length(), stopAtOtherChar);
  }

  @Contract(pure = true)
  public static int countChars(@NotNull CharSequence text, char c, int start, int end, boolean stopAtOtherChar) {
    boolean forward = start <= end;
    start = forward ? Math.max(0, start) : Math.min(text.length(), start);
    end = forward ? Math.min(text.length(), end) : Math.max(0, end);
    int count = 0;
    for (int i = forward ? start : start - 1; forward == i < end; i += forward ? 1 : -1) {
      if (text.charAt(i) == c) {
        count++;
      }
      else if (stopAtOtherChar) {
        break;
      }
    }
    return count;
  }

  @Contract(mutates = "param2")
  public static @NotNull StringBuilder escapeToRegexp(@NotNull CharSequence text, @NotNull StringBuilder builder) {
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ' ' || Character.isLetter(c) || Character.isDigit(c) || c == '_') {
        builder.append(c);
      }
      else if (c == '\n') {
        builder.append("\\n");
      }
      else if (c == '\r') {
        builder.append("\\r");
      }
      else {
        final Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == Character.UnicodeBlock.HIGH_SURROGATES || block == Character.UnicodeBlock.LOW_SURROGATES) {
          builder.append(c);
        } else {
          builder.append('\\').append(c);
        }
      }
    }

    return builder;
  }

  /**
   * @return {@code text} with some standard XML entities replaced with corresponding characters, e.g. '{@code &lt;}' replaced with '<'
   */
  @Contract(pure = true)
  public static @NotNull String unescapeXmlEntities(@NotNull String text) {
    return replace(text, REPLACES_REFS, REPLACES_DISP);
  }

  /**
   * @return {@code text} with some characters replaced with standard XML entities, e.g. '<' replaced with '{@code &lt;}'
   */
  @Contract(pure = true)
  public static @NotNull String escapeXmlEntities(@NotNull String text) {
    return replace(text, REPLACES_DISP, REPLACES_REFS);
  }

  @Contract(pure = true)
  public static @NotNull String replace(@NotNull String text, @NotNull List<String> from, @NotNull List<String> to) {
    assert from.size() == to.size();
    StringBuilder result = null;
    replace:
    for (int i = 0; i < text.length(); i++) {
      for (int j = 0; j < from.size(); j += 1) {
        String toReplace = from.get(j);
        String replaceWith = to.get(j);

        final int len = toReplace.length();
        if (len == 0) continue;
        if (text.regionMatches(i, toReplace, 0, len)) {
          if (result == null) {
            result = new StringBuilder(text.length());
            result.append(text, 0, i);
          }
          result.append(replaceWith);
          //noinspection AssignmentToForLoopParameter
          i += len - 1;
          continue replace;
        }
      }

      if (result != null) {
        result.append(text.charAt(i));
      }
    }
    return result == null ? text : result.toString();
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(T @NotNull [] items, @NotNull Function<? super T, String> f, @NotNull String separator) {
    return join(Arrays.asList(items), f, separator);
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(@NotNull Collection<? extends T> items,
                                         @NotNull Function<? super T, String> f,
                                         @NotNull String separator) {
    if (items.isEmpty()) return "";
    if (items.size() == 1) return notNullize(f.fun(items.iterator().next()));
    return join((Iterable<? extends T>)items, f, separator);
  }

  @Contract(pure = true)
  public static @NotNull String join(@NotNull Iterable<?> items, @NotNull String separator) {
    StringBuilder result = new StringBuilder();
    for (Object item : items) {
      result.append(item).append(separator);
    }
    if (result.length() > 0) {
      result.setLength(result.length() - separator.length());
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static @NotNull <T> String join(@NotNull Iterable<? extends T> items,
                                         @NotNull Function<? super T, ? extends CharSequence> f,
                                         @NotNull String separator) {
    StringBuilder result = new StringBuilder();
    join(items, f, separator, result);
    return result.toString();
  }

  @Contract(mutates = "param4")
  public static <T> void join(@NotNull Iterable<? extends T> items,
                              @NotNull Function<? super T, ? extends CharSequence> f,
                              @NotNull String separator,
                              @NotNull StringBuilder result) {
    boolean isFirst = true;
    for (T item : items) {
      CharSequence string = f.fun(item);
      if (!isEmpty(string)) {
        if (isFirst) {
          isFirst = false;
        }
        else {
          result.append(separator);
        }
        result.append(string);
      }
    }
  }

  @Contract(pure = true)
  public static @NotNull String join(@NotNull Collection<String> strings, @NotNull String separator) {
    if (strings.size() <= 1) {
      return notNullize(strings.isEmpty() ? null : strings.iterator().next());
    }
    StringBuilder result = new StringBuilder();
    join(strings, separator, result);
    return result.toString();
  }

  @Contract(mutates = "param3")
  public static void join(@NotNull Collection<String> strings, @NotNull String separator, @NotNull StringBuilder result) {
    boolean isFirst = true;
    for (String string : strings) {
      if (string != null) {
        if (isFirst) {
          isFirst = false;
        }
        else {
          result.append(separator);
        }
        result.append(string);
      }
    }
  }

  @Contract(pure = true)
  public static @NotNull String join(int @NotNull [] values, @NotNull String separator) {
    if (values.length == 0) return "";

    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) result.append(separator);
      result.append(values[i]);
    }
    return result.toString();
  }

  @Contract(pure = true)
  public static @NotNull String join(String @NotNull ... strings) {
    if (strings.length == 0) return "";
    if (strings.length == 1) return strings[0];

    final StringBuilder builder = new StringBuilder();
    for (String string : strings) {
      builder.append(string);
    }
    return builder.toString();
  }

  @Contract(pure = true)
  public static boolean isWhiteSpace(char c) {
    return c == '\n' || c == '\t' || c == ' ';
  }

  @Contract(pure = true)
  public static int stringHashCodeIgnoreWhitespaces(@NotNull CharSequence chars) {
    return StringsKmp.stringHashCodeIgnoreWhitespaces(chars);
  }

  @Contract(pure = true)
  public static boolean equalsIgnoreWhitespaces(@Nullable CharSequence s1, @Nullable CharSequence s2) {
    if (s1 == null ^ s2 == null) {
      return false;
    }

    if (s1 == null) {
      return true;
    }

    return StringsKmp.equalsIgnoreWhitespaces(s1, s2);
  }

  @Contract(pure = true)
  public static boolean equalsTrimWhitespaces(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    return StringsKmp.equalsTrimWhitespaces(s1, s2);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text) {
    return StringUtilRt.convertLineSeparators(text);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, boolean keepCarriageReturn) {
    return StringUtilRt.convertLineSeparators(text, keepCarriageReturn);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, @NotNull String newSeparator) {
    return StringUtilRt.convertLineSeparators(text, newSeparator);
  }

  @Contract(pure = true)
  public static @NotNull String convertLineSeparators(@NotNull String text, @NotNull String newSeparator, int @Nullable [] offsetsToKeep) {
    return StringUtilRt.convertLineSeparators(text, newSeparator, offsetsToKeep);
  }

  /**
   * Returns {@code true} if {@code s1} and {@code s2} refer to the same instance of {@link String}.
   * There are only few cases when you really need to use this method instead of {@link String#equals} or {@link Objects#equals}:
   * <ul>
   * <li>for small performance improvement if you're sure that there will be no different instances of the same string;</li>
   * <li>to implement "Sentinel" pattern; in that case use {@link String#String(String)} constructor to create the sentinel instance.</li>
   * </ul>
   */
  @SuppressWarnings({"StringEquality", "StringEqualitySSR"})
  @Contract(pure = true)
  public static boolean areSameInstance(@Nullable String s1, @Nullable String s2) {
    return s1 == s2;
  }
}