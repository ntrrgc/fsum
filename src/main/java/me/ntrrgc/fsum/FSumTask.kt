package me.ntrrgc.fsum

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.UnicastProcessor

abstract class FSumTask {
    var progress: TaskProgressClient? = null
        protected set(value) {
            if (field != value) {
                field = value
                onProgressTypeChanged?.invoke()
            }
        }
    var onProgressTypeChanged: (() -> Unit)? = null

    private val incidentsProcessor = UnicastProcessor.create<Incident>()
    val incidents: Flowable<Incident> get() = incidentsProcessor // For UI
    protected val incidentLogger = object : IncidentLogger { // For Task subclasses
        override fun log(incident: Incident) {
            incidentsProcessor.onNext(incident)
        }
    }

    abstract fun start(): Single<FinishMessage>

    data class FinishMessage(
            val message: String
    )
}