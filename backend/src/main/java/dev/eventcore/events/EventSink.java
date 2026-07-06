package dev.eventcore.events;

/**
 * The outbound port an appended event is handed to for downstream delivery.
 * Owned by the events package and implemented outside it, so events depends on
 * no other capability — the dependency points inward, toward this contract.
 */
public interface EventSink {

    void enqueue(Event event);
}
