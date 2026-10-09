package com.yaz.calculator.engine

import com.hp.creals.CR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/** What a computation came to: a value with its text, or why there is none. */
sealed interface Outcome {
    data class Value(val value: UnifiedReal, val written: Written) : Outcome
    data class Failed(val error: CalcError) : Outcome
}

/**
 * Runs [work] off the main thread and stops it after [timeoutMs]: the
 * engine checks for interruption while it computes, so a runaway
 * calculation (sin of 10^10000) stops instead of draining the battery.
 */
suspend fun compute(timeoutMs: Long, work: () -> Outcome): Outcome =
    withTimeoutOrNull(timeoutMs) {
        runInterruptible(Dispatchers.Default) {
            try {
                work()
            } catch (e: CalcException) {
                Outcome.Failed(e.error)
            } catch (_: UnifiedReal.ZeroDivisionException) {
                Outcome.Failed(CalcError.DIVIDE_BY_ZERO)
            } catch (_: BoundedRational.ZeroDivisionException) {
                Outcome.Failed(CalcError.DIVIDE_BY_ZERO)
            } catch (_: CR.AbortedException) {
                Outcome.Failed(CalcError.TIMEOUT)
            } catch (_: CR.PrecisionOverflowException) {
                Outcome.Failed(CalcError.TOO_LARGE)
            } catch (_: ArithmeticException) {
                Outcome.Failed(CalcError.DOMAIN)
            } catch (_: IllegalStateException) {
                // Something the computation needs is missing (a unit, a rate): no value, never a crash.
                Outcome.Failed(CalcError.SYNTAX)
            } catch (_: StackOverflowError) {
                Outcome.Failed(CalcError.TOO_LARGE)
            } catch (_: OutOfMemoryError) {
                Outcome.Failed(CalcError.TOO_LARGE)
            }
        }
    } ?: Outcome.Failed(CalcError.TIMEOUT)
