package blossom.tools;

import blossom.exception.BlossomError;
import blossom.interpreter.Interpreter;
import blossom.value.Value;

/**
 * Scratch driver, non un test: prova un mucchio di frammenti ed elenca cosa succede.
 * Non fa parte della suite ufficiale.
 */
public final class FuzzRunner
{
    private static final String IMPORTS = """
            import "core.list" as list;
            import "core.map" as map;
            import "core.math" as math;
            import "core.string" as string;
            import "core.system" as system;
            import "core.json" as json;
            """;

    private static void tryOne(String label, String code)
    {
        try
        {
            Value v = Interpreter.standalone().eval(IMPORTS + code);
            System.out.println("OK   [" + label + "] -> " + v.getString());
        }
        catch (BlossomError e)
        {
            System.out.println("ERR  [" + label + "] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("CRASH[" + label + "] " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        }
        catch (StackOverflowError e)
        {
            System.out.println("SOE  [" + label + "]");
        }
    }

    public static void main(String[] args)
    {
        // --- numbers: div/mod by zero consistency ---
        tryOne("div0", "1 / 0");
        tryOne("mod0", "1 % 0");
        tryOne("neg_mod0", "-1 % 0");
        tryOne("pow_neg_frac", "(-8) ^ (1/3)");
        tryOne("bigpow", "2 ^ 1000000");
        tryOne("nan_compare", "(0/0) == (0/0)");
        tryOne("float_precision", "0.1 + 0.2");
        tryOne("int_overflow", "9223372036854775807 + 1");
        tryOne("neg_zero", "-0 == 0");
        tryOne("string_to_num_coerce", "'5' + 3");
        tryOne("string_num_multiply", "'5' * 3");

        // --- access ':' ---
        tryOne("list_wrap_index", "[10,20,30]:4");            // doc says wraps, expect 20
        tryOne("list_neg_index_no_paren", "[10,20,30]:-1");   // doc says does NOT parse
        tryOne("list_neg_index_paren", "[10,20,30]:(-1)");    // doc says 30
        tryOne("string_index", "'abc':0");                    // doc says null
        tryOne("map_missing_key", "{'a'->1}:'b'");             // expect null
        tryOne("empty_list_index", "[]:0");                    // wrap over zero-length?
        tryOne("nested_mutation", "l = [[1,2],[3,4]]; l:0:0 = 99; l");
        tryOne("list_self_ref_print", "l = [1,2]; l:0 = l; system.type(l:0)");
        tryOne("list_is_reference", "a=[1,2]; b=a; b:0=99; a");   // expect a mutated too, per docs
        tryOne("list_remove_oob", "l=[1,2,3]; list.remove(l,7)"); // doc: IndexOutOfBoundsError
        tryOne("list_concat_diff_len", "[1,2] + [1,2,3]");        // doc: TypeMismatchError
        tryOne("list_plus_scalar", "[1,2] + 1");                  // doc: [2,3]
        tryOne("list_plus_list", "[1,2] + [10,20]");              // doc: [11,22]

        // --- functions / closures ---
        tryOne("recursion_deep", "fn f(n) -> if(n <= 0, 0, f(n-1)); f(100000)");
        tryOne("mutual_recursion", "fn a(n) -> if(n<=0, 0, b(n-1)); fn b(n) -> if(n<=0, 1, a(n-1)); a(50000)");
        tryOne("closure_capture", "fn make() -> (x = 0; fn() -> (x = x + 1; x)); f = make(); f(); f(); f()");
        tryOne("wrong_arity_too_few", "fn f(a,b) -> a+b; f(1)");
        tryOne("wrong_arity_too_many", "fn f(a,b) -> a+b; f(1,2,3)");
        tryOne("call_non_function", "x = 5; x()");

        // --- control flow ---
        tryOne("break_outside_loop", "break");
        tryOne("continue_outside_loop", "continue");
        tryOne("return_outside_function", "return 5");
        tryOne("chained_comparisons", "1 < 2 < 3");     // doc: true<3, defined but weird
        tryOne("logic_shortcircuit", "false && (1/0)"); // doc: false, div never runs
        tryOne("logic_returns_value", "0 || 5");         // doc: 5

        // --- try/catch/throw semantics from guide chapter 7 ---
        tryOne("try_no_filter_catches_language_error", "try(1/0)");          // 1/0 -> INFINITY, not error; use undefined var
        tryOne("try_catches_arity_error", "fn f(a,b)->a+b; try(f(1))");      // doc: try does NOT catch ArityError
        tryOne("try_catches_type_mismatch", "try([1,2] + [1,2,3])");        // doc: NOT caught
        tryOne("try_user_exception_short_form", "try(throw('boom'), 'caught')");
        tryOne("try_json_error_short_form_not_caught", "try(json.decode('{bad'), 'fallback')"); // doc: short form only catches user_exception
        tryOne("try_json_error_with_filter", "try(json.decode('{bad'), 'json_error', 'fallback')");
        tryOne("throw_default", "throw()");
        tryOne("throw_with_message", "throw('msg')");
        tryOne("throw_bad_type", "throw('nope_exception', 'x')"); // doc: UndefinedSymbolError, immediate
        tryOne("throw_hierarchy_catch_by_exception", "try(throw('value_exception','bad'), 'exception', 'caught')");
        tryOne("throw_hierarchy_wrong_filter_passthrough", "try(throw('value_exception','bad'), 'io_exception', 'caught')");

        // --- pipe ---
        tryOne("pipe_basic", "[1,2,3,4] |> list.filter(fn(x) -> x % 2 == 0) |> list.map(fn(x) -> x * 2)");
        tryOne("pipe_bare_function", "5 |> math.abs");

        // --- regex/search ---
        tryOne("search_regex_basic", "'hello world' ~ 'o w'");
        tryOne("search_regex_groups", "'v1.2' ~ '([0-9])\\\\.([0-9])'");
        tryOne("search_list_index", "['a','b','c'] ~ 'c'");
        tryOne("search_list_absent", "['a','b','c'] ~ 'z'");
        tryOne("search_map_key", "{'a'->1} ~ 'a'");

        // --- interpolation (double quotes) ---
        tryOne("interp_basic", "x = 5; \"value is ${x}\"");
        tryOne("interp_nested_call", "\"twice is ${math.abs(-4) * 2}\"");
        tryOne("interp_div_by_zero_inside", "\"x is ${1/0}\"");
        tryOne("single_quote_no_interp", "name='ada'; 'hello ${name}'");

        // --- parsing edge cases ---
        tryOne("empty_program", "");
        tryOne("only_whitespace", "   \n  ");
        tryOne("only_comment", "// just a comment");
        tryOne("unterminated_string", "'abc");
        tryOne("unterminated_paren", "(1 + 2");
        tryOne("deeply_nested_parens", "(".repeat(1000) + "1" + ")".repeat(1000));
        tryOne("very_long_identifier", "x".repeat(5000) + " = 1; " + "x".repeat(5000));
        tryOne("many_params", "fn f(" + String.join(",", java.util.Collections.nCopies(300, "a")) + ") -> 1");

        // --- json ---
        tryOne("json_roundtrip", "json.decode(json.encode({'a'->1,'b'->[1,2,3]}))");
        tryOne("json_circular", "l = []; l:0 = l; json.encode(l)");

        // --- misc semantics ---
        tryOne("string_repeat_huge", "list.size(string.split('', 'a' * 5000000))");
        tryOne("list_var_scope_in_loop", "for([1,2,3], y = _); y");  // does loop var leak scope?
        tryOne("map_iteration_key_types", "m = {}; m:1 = 'a'; m:'1' = 'b'; list.size(map.pairs(m))");
        tryOne("for_continue", "sum=0; for([1,2,3], if(_==2, continue()); sum += _); sum");
        tryOne("for_break", "sum=0; for([1,2,3,4], if(_==3, break()); sum += _); sum");
        tryOne("underscore_i_index", "r=[]; for(['a','b','c'], r += (_i + ':' + _)); r");
        tryOne("break_bare_word", "break");
        tryOne("continue_bare_word", "continue");
    }
}
