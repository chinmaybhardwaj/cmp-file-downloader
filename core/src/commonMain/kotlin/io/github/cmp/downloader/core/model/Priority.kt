package io.github.cmp.downloader.core.model

/**
 * Priority levels for scheduling download tasks in the queue.
 * Tasks with higher numerical weight are dequeued first.
 */
enum class Priority(val weight: Int) {
    LOW(10),
    NORMAL(20),
    HIGH(30),
    CRITICAL(40);

    companion object {
        fun fromWeight(weight: Int): Priority {
            return entries.firstOrNull { it.weight == weight } ?: NORMAL
        }
    }
}
