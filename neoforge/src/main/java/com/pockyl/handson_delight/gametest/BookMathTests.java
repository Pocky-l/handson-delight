package com.pockyl.handson_delight.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_delight.BookMath;
import com.pockyl.handson_delight.HandsOnDelight;

/** The recipe book's arithmetic: turning pages, leafing through them with a held click, and what a search finds. */
@GameTestHolder(HandsOnDelight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BookMathTests {
    private BookMathTests() {
    }

    /** The book's foot and wheel: kept within the list, round the ends only when asked, nothing to turn with one page. */
    @GameTest(template = "empty")
    public static void bookTurns(GameTestHelper helper) {
        helper.assertTrue(BookMath.turned(0, -1, 5, false) == 0, "back from the first page stays");
        helper.assertTrue(BookMath.turned(4, 1, 5, false) == 4, "on from the last page stays");
        helper.assertTrue(BookMath.turned(4, 1, 5, true) == 0, "a click on from the last page goes to the first");
        helper.assertTrue(BookMath.turned(0, -1, 5, true) == 4, "a click back from the first page goes to the last");
        helper.assertTrue(BookMath.turned(2, 1, 5, false) == 3, "a turn on");
        helper.assertTrue(BookMath.turned(0, 1, 1, true) == 0, "one page has nothing to turn");
        helper.succeed();
    }

    /** A held click: one page at once, a wait, then quicker and quicker. */
    @GameTest(template = "empty")
    public static void bookLeafing(GameTestHelper helper) {
        helper.assertTrue(BookMath.turnsDue(0.0F) == 1, "one page at once");
        helper.assertTrue(BookMath.turnsDue(0.3F) == 1, "then a wait");
        helper.assertTrue(BookMath.turnsDue(0.36F) == 2, "then it goes on");
        int atOneAndAHalf = BookMath.turnsDue(1.5F);
        helper.assertTrue(atOneAndAHalf >= 9 && atOneAndAHalf <= 12, "about ten pages in a second and a half: " + atOneAndAHalf);
        helper.assertTrue(BookMath.turnsDue(2.5F) - atOneAndAHalf >= 15, "faster after that");
        helper.assertTrue(BookMath.repeatDue(0.36F, 1) && !BookMath.repeatDue(0.36F, 2), "one turn per page due");
        helper.succeed();
    }

    /** The book's search: every word in the name or the id, case aside. */
    @GameTest(template = "empty")
    public static void bookSearch(GameTestHelper helper) {
        helper.assertTrue(BookMath.matches("beef st", "Beef Stew", "farmersdelight:beef_stew"), "words of the name");
        helper.assertTrue(BookMath.matches("youkai", "Tuna Nigiri", "youkaisfeasts:tuna_nigiri"), "the mod of the id");
        helper.assertTrue(BookMath.matches("", "Tomato Sauce", "farmersdelight:tomato_sauce"), "nothing typed finds all");
        helper.assertTrue(!BookMath.matches("pumpkin", "Beef Stew", "farmersdelight:beef_stew"), "another dish is not found");
        helper.assertTrue(BookMath.matches("STEW", "Fish Stew", "farmersdelight:fish_stew"), "case aside");
        helper.succeed();
    }
}
