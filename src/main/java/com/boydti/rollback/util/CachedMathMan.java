/*
 * Rollback for Nukkit
 *
 * Copyright (C) 2017-2020 boy0001 and larryTheCoder
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.boydti.rollback.util;

public class CachedMathMan {
    private static final int ATAN2_BITS = 7;
    private static final int ATAN2_BITS2 = ATAN2_BITS << 1;
    private static final int ATAN2_MASK = ~(-1 << ATAN2_BITS2);
    private static final int ATAN2_COUNT = ATAN2_MASK + 1;
    private static final int ATAN2_DIM = (int) Math.sqrt(ATAN2_COUNT);
    private static final float INV_ATAN2_DIM_MINUS_1 = 1.0f / (ATAN2_DIM - 1);
    private static final float[] atan2 = new float[ATAN2_COUNT];

    static {
        for (int i = 0; i < ATAN2_DIM; i++) {
            for (int j = 0; j < ATAN2_DIM; j++) {
                float x0 = (float) i / ATAN2_DIM;
                float y0 = (float) j / ATAN2_DIM;

                atan2[(j * ATAN2_DIM) + i] = (float) Math.atan2(y0, x0);
            }
        }
    }

    private static float[] ANGLES = new float[65536];
    static {
        for (int i = 0; i < 65536; ++i) {
            ANGLES[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
        }
    }

    private static char[] SQRT = new char[65536];
    static {
        for (int i = 0; i < SQRT.length; i++) {
            SQRT[i] = (char) Math.round(Math.sqrt(i));
        }
    }

    /**
     * Optimized for i elem 0,65536 (characters)
     * @param i
     * @return square root
     */
    protected static int usqrt(int i) {
        return SQRT[i];
    }

    protected static float sinInexact(double paramFloat) {
        return ANGLES[(int) (paramFloat * 10430.378F) & 0xFFFF];
    }

    protected static float cosInexact(double paramFloat) {
        return ANGLES[(int) (paramFloat * 10430.378F + 16384.0F) & 0xFFFF];
    }

    protected static float atan2(float y, float x) {
        float add, mul;

        if (x < 0.0f) {
            if (y < 0.0f) {
                x = -x;
                y = -y;

                mul = 1.0f;
            } else {
                x = -x;
                mul = -1.0f;
            }

            add = (float) -Math.PI;
        } else {
            if (y < 0.0f) {
                y = -y;
                mul = -1.0f;
            } else {
                mul = 1.0f;
            }

            add = 0.0f;
        }

        float invDiv = 1.0f / ((Math.max(x, y)) * INV_ATAN2_DIM_MINUS_1);

        int xi = (int) (x * invDiv);
        int yi = (int) (y * invDiv);

        return (atan2[(yi * ATAN2_DIM) + xi] + add) * mul;
    }
}