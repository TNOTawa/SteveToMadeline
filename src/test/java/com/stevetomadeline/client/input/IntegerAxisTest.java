package com.stevetomadeline.client.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntegerAxisTest {
    @Test
    void newerOppositeDirectionWinsUntilReleased() {
        IntegerAxis axis = new IntegerAxis();
        assertEquals(1, axis.update(false, true));
        assertEquals(-1, axis.update(true, true));
        assertEquals(-1, axis.update(true, true));
        assertEquals(1, axis.update(false, true));
        assertEquals(0, axis.update(false, false));
    }

    @Test
    void simultaneousOppositePressesFromRestAreNeutral() {
        IntegerAxis axis = new IntegerAxis();
        assertEquals(0, axis.update(true, true));
        assertEquals(-1, axis.update(true, false));
        assertEquals(1, axis.update(true, true));
    }
}
