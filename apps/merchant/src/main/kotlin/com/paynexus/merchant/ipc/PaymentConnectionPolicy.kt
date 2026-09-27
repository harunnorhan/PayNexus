package com.paynexus.merchant.ipc

import com.paynexus.payment.contract.PaymentIpcContract

/** Main-thread-owned connection decisions, independent of Android binding resources. */
internal class PaymentConnectionPolicy {
    class Attempt

    class Session

    sealed interface State {
        data object Disconnected : State
        data object Binding : State
        data object CheckingCompatibility : State
        data object Ready : State
        data class Unavailable(val reason: Failure) : State
        data class Incompatible(val remoteVersion: Int) : State
    }

    enum class Failure {
        BindFailed,
        PermissionDenied,
        NullBinding,
        VersionQueryFailed,
        ConnectionLost,
        WorkerUnavailable,
    }

    var state: State = State.Disconnected
        private set
    private var session: Session? = null
    private var attempt: Attempt? = null
    private var recoveryUsed = false
    private var pendingRecovery: Session? = null

    fun start(): Attempt? {
        if (session != null) return null
        session = Session()
        recoveryUsed = false
        return newAttempt()
    }

    fun stop() {
        session = null
        attempt = null
        pendingRecovery = null
        state = State.Disconnected
    }

    fun isCurrent(candidate: Attempt): Boolean = session != null && attempt === candidate

    fun connected(candidate: Attempt): Boolean {
        if (!isCurrent(candidate) || state != State.Binding) return false
        state = State.CheckingCompatibility
        return true
    }

    fun versionReceived(candidate: Attempt, remoteVersion: Int): Boolean {
        if (!isCurrent(candidate) || state != State.CheckingCompatibility) return false
        state = if (PaymentIpcContract.supports(remoteVersion)) {
            State.Ready
        } else {
            attempt = null
            State.Incompatible(remoteVersion)
        }
        return true
    }

    fun failed(candidate: Attempt, reason: Failure): Boolean {
        if (!isCurrent(candidate)) return false
        attempt = null
        state = State.Unavailable(reason)
        return true
    }

    fun lost(candidate: Attempt): Session? {
        if (!failed(candidate, Failure.ConnectionLost) || recoveryUsed) return null
        recoveryUsed = true
        return session.also { pendingRecovery = it }
    }

    fun recover(candidate: Session): Attempt? {
        if (session !== candidate || pendingRecovery !== candidate) return null
        pendingRecovery = null
        return newAttempt()
    }

    private fun newAttempt(): Attempt = Attempt().also {
        attempt = it
        state = State.Binding
    }
}
