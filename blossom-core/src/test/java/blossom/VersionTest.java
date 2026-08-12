package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import blossom.project.ApiLevel;
import blossom.project.Version;
import blossom.project.VersionRange;

/** Semver e vincoli di versione, e la regola di compatibilità dell'{@code api_level}. */
class VersionTest
{
    private static boolean accepts(String range, String version)
    {
        return VersionRange.parse(range).accepts(Version.parse(version));
    }

    @Test
    void parsesTheThreeNumbersAndThePreRelease()
    {
        assertEquals(new Version(1, 2, 3, null), Version.parse("1.2.3"));
        assertEquals(new Version(1, 0, 0, "rc.1"), Version.parse("1.0.0-rc.1"));
        assertEquals("1.0.0-rc.1", Version.parse("1.0.0-rc.1").toString());
    }

    @Test
    void refusesWhatIsNotAVersion()
    {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.x"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("01.2.3"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3-"));
    }

    @Test
    void ordersByNumbersThenByPreRelease()
    {
        assertTrue(Version.parse("1.0.0").compareTo(Version.parse("0.9.9")) > 0);
        assertTrue(Version.parse("1.0.1").compareTo(Version.parse("1.0.0")) > 0);
        // una release viene dopo il suo pre-release, che e' il punto di avere i pre-release
        assertTrue(Version.parse("1.0.0").compareTo(Version.parse("1.0.0-rc1")) > 0);
        assertTrue(Version.parse("1.0.0-alpha").compareTo(Version.parse("1.0.0-beta")) < 0);
        assertTrue(Version.parse("1.0.0-2").compareTo(Version.parse("1.0.0-alpha")) < 0, "numerici prima degli alfanumerici");
        assertTrue(Version.parse("1.0.0-rc").compareTo(Version.parse("1.0.0-rc.1")) < 0, "meno identificatori precede");
    }

    @Test
    void caretMeansCompatible()
    {
        assertTrue(accepts("^1.2.0", "1.2.0"));
        assertTrue(accepts("^1.2.0", "1.9.9"));
        assertFalse(accepts("^1.2.0", "1.1.9"));
        assertFalse(accepts("^1.2.0", "2.0.0"));
    }

    @Test
    void belowOneZeroEveryBumpCanBreak()
    {
        // e' li' che sta quasi tutto quello che e' giovane, quindi non e' un caso limite
        assertTrue(accepts("^0.2.3", "0.2.9"));
        assertFalse(accepts("^0.2.3", "0.3.0"));
        assertTrue(accepts("^0.0.3", "0.0.3"));
        assertFalse(accepts("^0.0.3", "0.0.4"));
    }

    @Test
    void tildeMeansPatchesOnly()
    {
        assertTrue(accepts("~1.2.0", "1.2.9"));
        assertFalse(accepts("~1.2.0", "1.3.0"));
    }

    @Test
    void comparisonsAndCombinations()
    {
        assertTrue(accepts(">=1.2.0", "3.0.0"));
        assertFalse(accepts(">=1.2.0", "1.1.0"));
        assertTrue(accepts(">=1.2.0, <2.0.0", "1.9.0"));
        assertFalse(accepts(">=1.2.0, <2.0.0", "2.0.0"));
        assertFalse(accepts(">1.2.0", "1.2.0"));
        assertTrue(accepts("*", "9.9.9"));
        assertTrue(accepts("1.2.3", "1.2.3"));
        assertFalse(accepts("1.2.3", "1.2.4"));
    }

    @Test
    void aPreReleaseIsNeverPickedUpByAccident()
    {
        // altrimenti basterebbe pubblicare una beta per installarla a tutti
        assertFalse(accepts("^1.0.0", "1.5.0-rc1"));
        assertFalse(accepts(">=1.0.0", "2.0.0-beta"));
        assertTrue(accepts(">=1.0.0-rc1", "1.0.0-rc2"));
    }

    @Test
    void refusesTheAlternationOperatorWithAnExplanation()
    {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parse("^1.0.0 || ^2.0.0"));
        assertTrue(error.getMessage().contains("||"), error.getMessage());
    }

    @Test
    void picksTheHighestThatFits()
    {
        List<Version> available = List.of(
                Version.parse("1.0.0"), Version.parse("1.4.2"), Version.parse("2.0.0"), Version.parse("1.9.0-rc1"));
        assertEquals(Version.parse("1.4.2"), VersionRange.parse("^1.0.0").best(available));
        assertEquals(Version.parse("2.0.0"), VersionRange.parse("*").best(available));
    }

    // --- api_level ---

    @Test
    void anAppRunsWhenTheMajorMatchesAndTheMinorIsNotHigher()
    {
        ApiLevel runtime = new ApiLevel(1, 3);
        assertTrue(new ApiLevel(1, 0).isSupportedBy(runtime));
        assertTrue(new ApiLevel(1, 3).isSupportedBy(runtime));
        assertFalse(new ApiLevel(1, 4).isSupportedBy(runtime), "l'app usa qualcosa che non c'e' ancora");
        assertFalse(new ApiLevel(2, 0).isSupportedBy(runtime), "e' un'altra API");
        assertFalse(new ApiLevel(0, 9).isSupportedBy(runtime));
    }

    @Test
    void theTwoIncompatibilitiesAreExplainedDifferently()
    {
        ApiLevel runtime = new ApiLevel(1, 3);
        String tooNew = assertThrows(blossom.exception.CompatibilityError.class,
                () -> new ApiLevel(1, 9).requireSupportedBy(runtime, "app", blossom.exception.SourcePosition.UNKNOWN))
                .getMessage();
        assertTrue(tooNew.contains("update the mod"), tooNew);

        String otherApi = assertThrows(blossom.exception.CompatibilityError.class,
                () -> new ApiLevel(2, 0).requireSupportedBy(runtime, "app", blossom.exception.SourcePosition.UNKNOWN))
                .getMessage();
        assertTrue(otherApi.contains("two different APIs"), otherApi);
    }
}
