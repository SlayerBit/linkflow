package com.linkflow.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    @Test
    void encodeZero_returnsSingleZeroChar() {
        assertEquals("0", Base62.encode(0));
    }

    @Test
    void encodeAndDecode_roundTrip() {
        long value = 123456789L;
        String encoded = Base62.encode(value);
        assertEquals(value, Base62.decode(encoded));
    }

    @Test
    void decode_invalidCharacter_throws() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc!"));
    }
}
