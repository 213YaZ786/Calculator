package com.hp.creals;

/**
 * Where a number's most significant bit lies, which CR keeps to its own
 * package. Calculator's own file, beside AOSP's crcalc left as it is.
 */
public final class Msd {

    private Msd() {}

    /**
     * The position of the most significant bit of [x]: 2^(m-1) < |x| < 2^(m+1).
     * Integer.MIN_VALUE when |x| is below 2^n, so a zero never loops.
     */
    public static int of(CR x, int n) {
        return x.iter_msd(n);
    }
}
