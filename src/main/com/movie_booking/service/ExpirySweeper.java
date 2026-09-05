package com.movie_booking.service;

import com.movie_booking.util.AppConfig;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A background thread that reclaims seat holds nobody came back for.
 *
 * <h2>Why a sweeper is needed at all</h2>
 *
 * Reads already treat a lapsed hold as free - the seat map and every availability
 * count include {@code LOCKED AND locked_until &lt; NOW()} as sellable - so the
 * system stays <em>correct</em> without this class. What it does is keep the data
 * honest: without it, {@code show_seats} would slowly fill with rows that claim to
 * be LOCKED by users who left hours ago, and bookings would sit at PENDING for
 * ever. Any report counting "seats currently held" or "bookings awaiting payment"
 * would be nonsense, and the index on {@code (status, locked_until)} would grow
 * full of dead entries.
 *
 * <p>So expiry is enforced in two places on purpose: <b>lazily</b> at read time,
 * which is what makes it immediate and race-free, and <b>eagerly</b> here, which
 * is what keeps the stored state tidy. The lazy path is the one correctness
 * depends on; this is housekeeping.
 *
 * <h2>Why a daemon thread</h2>
 *
 * The executor is built with daemon threads so that this never keeps the JVM
 * alive. When the server stops, an in-flight sweep is not something worth waiting
 * for - whatever it does not finish will be picked up by the next start-up, and
 * every sweep is a self-contained transaction, so being killed part-way cannot
 * corrupt anything.
 */
public class ExpirySweeper {

    private static final long INTERVAL_SECONDS =
            AppConfig.getLong("booking.sweepIntervalSeconds", 60L);

    private final BookingService bookingService;
    private final ScheduledExecutorService scheduler;

    public ExpirySweeper(BookingService bookingService) {
        this.bookingService = bookingService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "booking-expiry-sweeper");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sweepOnce();
            }
        }, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);

        System.out.println("[sweeper] Reclaiming expired holds every "
                + INTERVAL_SECONDS + "s.");
    }

    /**
     * Runs one pass, swallowing anything that goes wrong.
     *
     * <p>This is the one place where catching {@link Throwable} is right.
     * {@code scheduleWithFixedDelay} silently cancels the whole schedule if a run
     * throws - so a single transient database blip would stop expiry for the rest
     * of the process lifetime, with no error anywhere. Logging and carrying on
     * means the next pass simply tries again.
     */
    private void sweepOnce() {
        try {
            int expired = bookingService.sweepExpiredHolds();
            if (expired > 0) {
                System.out.println("[sweeper] Released " + expired + " abandoned booking(s).");
            }
        } catch (Throwable failure) {
            System.err.println("[sweeper] Pass failed, will retry: " + failure.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
