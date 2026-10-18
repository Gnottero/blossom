package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import blossom.interpreter.Interpreter;

/**
 * The bugs Scarpet fixed between 26.3-beta-2 and 26.4-beta-3, rewritten in Blossom.
 *
 * <p>Each case comes from the test suite upstream added alongside the fix
 * ({@code tests_language.scl}): the interpreter was extracted from that code, so a bug fixed there
 * was very likely here too. Every case returns {@code true} when the behavior is the right one.
 */
class UpstreamParityTest
{
    private static final String IMPORTS = """
            import "core.list" as list;
            import "core.math" as math;
            import "core.string" as string;
            import "core.system" as system;
            """;

    private static void check(String code)
    {
        assertEquals("true", Interpreter.standalone().eval(IMPORTS + code).getString(), code);
    }

    @Test
    void hexadecimalArithmeticWithoutWhitespaceKeepsExponentDigits()
    {
        check("0xfe+2 == 256 && 0xfe-2 == 252 && 0X1E+2 == 32 && 0X1E-2 == 28 && 2e+2 == 200 && 2e-2 == 0.02");
    }

    @Test
    void aChainOfDifferencesTellsApartValuesWithCollidingHashes()
    {
        // 'a != b != c' is one single check that all are different, and it orders them by hash:
        // two different values with the same hash used to hide a duplicate behind them
        check("""
                string.hash_code('Aa') == string.hash_code('BB')
                    && ('Aa' != 'BB') && !('Aa' != 'BB' != 'Aa') && !('BB' != 'Aa' != 'BB')""");
        check("""
                ('AaAa' != 'BBBB' != 'AaBB' != 'BBAa')
                    && !('AaAa' != 'BBBB' != 'AaBB' != 'BBAa' != 'AaAa')
                    && !('AaAa' != 'BBBB' != 'AaBB' != 'BBAa' != 'BBBB')""");
        check("""
                first = {'Aa' -> 1}; second = {'BB' -> 1}; duplicate = {'Aa' -> 1};
                (first != second) && !(first != second != duplicate)
                    && ([first] != [second]) && !([first] != [second] != [duplicate])""");
        check("!(true != 1) && !(false != 0) && !(1 != '1') && (null != false)");
    }

    @Test
    void notAgreesWithTheUnaryOperator()
    {
        check("not(true) == false && not(false) == true && not(null) == true && not([]) == true "
                + "&& not([0]) == false && not('false') == false");
    }

    @Test
    void aDestructuredSwapKeepsTheAssignmentTargets()
    {
        check("""
                [a, b] = [1, 2]; [c, d] = [3, 4];
                [a, b] <> [c, d];
                a += 10; b = 40; c += 100; d = 20;
                [a, b, c, d] == [13, 40, 101, 20]""");
    }

    @Test
    void integerRangesStopBeforeTheStepOverflows()
    {
        check("""
                lowest = -9223372036854775807 - 1; highest = 9223372036854775807;
                list.slice(list.range(highest - 1, highest, 2), 0, 3) == [highest - 1]
                    && list.slice(list.range(lowest + 1, lowest, -2), 0, 3) == [lowest + 1]
                    && list.slice(list.range(highest, lowest, lowest), 0, 3) == [highest, -1]""");
    }

    @Test
    void fullWidthIndicesWrapWithoutOverflowing()
    {
        check("""
                lowest = -9223372036854775807 - 1; highest = 9223372036854775807;
                items = [10, 20, 30];
                list.get(items, highest) == 20 && list.get(items, lowest) == 20
                    && list.slice('abc', highest) == 'bc' && list.slice(items, lowest) == [20, 30]""");
        // unlike Scarpet, removing an index that is not there is an error and does not wrap around:
        // whoever removes expects that element to be gone afterwards
    }

    @Test
    void emptyStringsAndListsCanBeSliced()
    {
        check("list.slice([], 0) == [] && list.slice([], 0, 0) == [] && list.slice([], -1, 4) == [] "
                + "&& list.slice('', 0) == '' && list.slice('', 0, 0) == '' && list.slice('', -1, 4) == ''");
    }

    @Test
    void sortRestoresTheScopeWhenItsKeyThrows()
    {
        check("""
                list.map([41, 42], (
                    expected = _;
                    original = [2, 1];
                    try(list.sort(original, throw('stop')));
                    _ == expected && original == [2, 1]
                )) == [true, true]""");
    }

    @Test
    void forCountsTheSuccessfulBreakValues()
    {
        check("for([1], break(true)) == 1 && for([1], break(false)) == 0 && for([1], break()) == 0 "
                + "&& for([1, 2, 3], if(_ == 2, break(true), true)) == 2");
    }

    @Test
    void reduceRestoresTheAccumulatorOnEveryExit()
    {
        check("""
                seed = 7; answer = list.reduce([1, 2], _a + _, seed); seed = 9;
                normal_ok = answer == 10 && seed == 9;
                seed = 7; try(list.reduce([1], throw(_a), seed)); seed = 9;
                throw_ok = seed == 9;
                seed = 7; answer = list.reduce([1], break(_a), seed); seed = 9;
                break_ok = answer == 7 && seed == 9;
                seed = 7; answer = list.reduce([1, 2], continue(_a), seed); seed = 9;
                normal_ok && throw_ok && break_ok && answer == 7 && seed == 9""");
    }

    @Test
    void reduceKeepsExplicitMutationsOfTheAccumulator()
    {
        check("seed = []; answer = list.reduce([1, 2], list.put(_a, null, _); _a, seed); seed == [1, 2] && answer == seed");
    }

    @Test
    void loopAndWhileRestoreTheScopeAfterAnException()
    {
        check("""
                list.map([41, 42], (
                    expected = _;
                    try(loop(1, throw('stop'))); loop_ok = _ == expected;
                    try(while(true, throw('stop'))); while_body_ok = _ == expected;
                    try(while(throw('stop'), null)); while_condition_ok = _ == expected;
                    try(while(true, 1, throw('stop'))); bounded_body_ok = _ == expected;
                    try(while(throw('stop'), 1, null));
                    loop_ok && while_body_ok && while_condition_ok && bounded_body_ok && _ == expected
                )) == [true, true]""");
    }

    @Test
    void collectionLoopsRestoreTheImplicitVariablesAfterAnException()
    {
        check("""
                list.map([41, 42], (
                    _a = 99;
                    expected = [_, _i, _a];
                    try(list.map([7], throw('stop'))); map_ok = [_, _i, _a] == expected;
                    try(list.filter([7], throw('stop'))); filter_ok = [_, _i, _a] == expected;
                    try(list.find([7], throw('stop'))); first_ok = [_, _i, _a] == expected;
                    try(list.all([7], throw('stop'))); all_ok = [_, _i, _a] == expected;
                    try(for([7], throw('stop'))); for_ok = [_, _i, _a] == expected;
                    try(list.reduce([7], throw(_a), 99));
                    map_ok && filter_ok && first_ok && all_ok && for_ok && [_, _i, _a] == expected
                )) == [true, true]""");
    }

    @Test
    void anAbortedIterationRestoresTheInputBindings()
    {
        check("value = 7; try(list.map([value], throw('stop'))); value = 9; value == 9");
    }

    @Test
    void returningFromAnIterationRestoresSharedInputBindings()
    {
        check("""
                value = 7; items = [value];
                leave = fn(outer(items)) -> list.map(items, return(1));
                call(leave);
                value = 9;
                value == 9""");
    }

    @Test
    void anAbortedIterationResetsItsReusableIterator()
    {
        check("values = list.range(3); try(list.map(values, throw('stop'))); [values] == [0, 1, 2]");
    }

    @Test
    void tryRestoresTheScopeWhenHandlersAndFiltersThrow()
    {
        check("""
                list.map([41, 42], (
                    expected = _;
                    try(try(throw('inner'), throw('outer')));
                    handler_ok = _ == expected && system.vars('_') ~ '_trace' == null;
                    try(try(throw('inner'), throw('filter'), null));
                    handler_ok && _ == expected && system.vars('_') ~ '_trace' == null
                )) == [true, true]""");
    }

    @Test
    void tryRemovesItsTraceOnBreakAndContinue()
    {
        check("""
                loop(1, try(throw('stop'), break()));
                break_ok = system.vars('_') ~ '_trace' == null;
                loop(2, try(throw('stop'), continue()));
                break_ok && system.vars('_') ~ '_trace' == null""");
    }

    @Test
    void aNestedRethrowRestoresTheEnclosingCatch()
    {
        check("""
                try(throw('outer'), (
                    token = _trace:'token';
                    try(try(throw('inner'), throw('again')));
                    _ == 'outer' && _trace:'token' == token
                ))""");
    }

    @Test
    void unaryBitwiseOperationsKeepIntegerPrecision()
    {
        check("""
                math.bitwise_not(9007199254740993) == -9007199254740994
                    && math.bitwise_popcount(9007199254740993) == 2
                    && math.bitwise_not(9223372036854775806) == -9223372036854775807
                    && math.bitwise_popcount(9223372036854775806) == 62
                    && math.bitwise_not(-1) == 0 && math.bitwise_popcount(-1) == 64""");
        check("math.bitwise_not(1.5) == -2 && math.bitwise_popcount(1.5) == 1 "
                + "&& math.bitwise_not(-1.5) == 0 && math.bitwise_popcount(-1.5) == 64");
    }

    @Test
    void minAndMaxOfAnEmptyListAreNull()
    {
        check("math.min([]) == null && math.max([]) == null && system.type(math.min([])) == 'null' "
                + "&& math.min([null]) == null && math.max([null]) == null");
    }

    @Test
    void integerFormattingSaturatesWithoutChangingSign()
    {
        check("""
                string.str('%d', -1e100) == '-9223372036854775808' && string.str('%d', 1e100) == '9223372036854775807'
                    && string.str('%d', -9223372036854775807 - 1) == '-9223372036854775808'
                    && string.str('%d', -1.5) == '-2' && string.str('%d', 1.5) == '1'
                    && string.str('%d', 9007199254740993) == '9007199254740993'
                    && string.str('%x', 9223372036854775807) == '7fffffffffffffff'""");
    }
}
