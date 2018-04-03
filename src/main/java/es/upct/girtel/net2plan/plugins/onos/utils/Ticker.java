/*
 * Copyright (c) 2018 ACINO Consortium
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the Free Software Foundation License (version 3, or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package es.upct.girtel.net2plan.plugins.onos.utils;


import java.util.concurrent.TimeUnit;

/**
 * A time source; returns a time value representing the number of nanoseconds elapsed since some
 * fixed but arbitrary point in time. Note that most users should use {@link Stopwatch} instead of
 * interacting with this class directly.
 *
 * <p><b>Warning:</b> this interface can only be used to measure elapsed time, not wall time.
 *
 * @author Kevin Bourrillion
 * @since 10.0 (<a href="https://github.com/google/guava/wiki/Compatibility">mostly
 *     source-compatible</a> since 9.0)
 */

public abstract class Ticker {
    /**
     * Constructor for use by subclasses.
     */
    protected Ticker() {}

    /**
     * Returns the number of nanoseconds elapsed since this ticker's fixed point of reference.
     */
    public abstract long read();

    /**
     * A ticker that reads the current time using {@link System#nanoTime}.
     *
     * @since 10.0
     */
    public static Ticker systemTicker() {
        return SYSTEM_TICKER;
    }

    private static final Ticker SYSTEM_TICKER =
            new Ticker() {
                @Override
                public long read() {
                    return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
                }
            };
}
