package com.nectar.doodle.utils

class MutableTreeSetJs<E> constructor(comparator: Comparator<E>, elements: Collection<E>): TreeSetJs<E>(comparator, elements), MutableSet<E> {
    constructor(comparator: Comparator<E>): this(comparator, emptyList<E>())

    override fun iterator(): MutableIterator<E> = BstIterator()

    override fun add(element: E): Boolean = super.add(element)

    override fun remove(element: E): Boolean = super.remove_(element)

    override fun addAll(elements: Collection<E>) = elements.all { add(it) }

    override fun removeAll(elements: Collection<E>) = elements.all { remove(it) }

    override fun retainAll(elements: Collection<E>): Boolean {
        val set    = elements.toSet()
        var result = false

        asSequence().filterNot { it in set }.forEach { result = remove(it) }

        return result
    }

    override fun clear() { root = null }

    companion object {
        operator fun <T: Comparable<T>> invoke(): MutableTreeSet<T> = MutableTreeSet(Comparator { a, b -> a.compareTo(b) })
        operator fun <T: Comparable<T>> invoke(elements: Collection<T>): MutableTreeSet<T> = MutableTreeSet(Comparator { a, b -> a.compareTo(b) }, elements)
    }
}