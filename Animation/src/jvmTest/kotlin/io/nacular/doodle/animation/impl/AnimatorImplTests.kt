package io.nacular.doodle.animation.impl

import io.mockk.mockk
import io.mockk.verify
import io.nacular.doodle.animation.Animation
import io.nacular.doodle.animation.Animator
import io.nacular.doodle.animation.Animator.Listener
import io.nacular.doodle.animation.AnimatorImpl
import io.nacular.doodle.animation.invoke
import io.nacular.doodle.animation.loop
import io.nacular.doodle.animation.repeat
import io.nacular.doodle.animation.transition.linear
import io.nacular.doodle.animation.tween
import io.nacular.doodle.animation.tweenFloat
import io.nacular.doodle.scheduler.AnimationScheduler
import io.nacular.doodle.scheduler.Task
import io.nacular.doodle.time.Timer
import io.nacular.doodle.utils.Completable
import io.nacular.measured.units.Measure
import io.nacular.measured.units.Time
import io.nacular.measured.units.Time.Companion.hours
import io.nacular.measured.units.Time.Companion.milliseconds
import io.nacular.measured.units.times
import kotlin.test.Test
import kotlin.test.expect

/**
 * Created by Nicholas Eddy on 12/15/19.
 */
class AnimatorImplTests {
    private class MonotonicTimer(private val increment: Measure<Time> = 2 * milliseconds): Timer {
        var actual = 100 * milliseconds
            private set

        override val now get() = actual.also { actual += increment }
    }

    private class ImmediateAnimationScheduler: AnimationScheduler {
        override fun onNextFrame(job: (Measure<Time>) -> Unit) = object: Task {
            override val completed = true

            override fun cancel() {}
        }.also {
            job(2 * milliseconds)
        }
    }

    private class ManualAnimationScheduler: AnimationScheduler {
        private inner class ManualTask(private val job: (Measure<Time>) -> Unit): Task {
            override var completed = false

            init {
                activeTasks += this
            }

            fun complete() {
                completed = true
                job(1 * milliseconds)
            }

            override fun cancel() {
                activeTasks -= this
            }
        }

        private val activeTasks = mutableSetOf<ManualTask>()

        override fun onNextFrame(job: (Measure<Time>) -> Unit) = ManualTask(job)

        fun runOutstandingTasks() {
            val tasks = activeTasks.toSet()
            activeTasks.clear()

            tasks.forEach {
                it.complete()
            }
        }

        fun runToCompletion() {
            while (activeTasks.isNotEmpty()) {
                runOutstandingTasks()
            }
        }
    }

    @Test fun `animates increasing number property`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ManualAnimationScheduler() //ImmediateAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()
        val onCompleted        = mockk<(Completable) -> Unit>()

        val outputs = mutableListOf<Float>()

        animate.listeners += listener

        val animation = animate.invoke(0f to 1f, tweenFloat(linear, 3 * milliseconds)) {
            outputs += it
        }.apply {
            completed += onCompleted
        }

        animationScheduler.runToCompletion()

        expect(listOf(0f, 2/3f, 1f)) { outputs }

        verify(exactly = 2) { listener.changed  (animate, setOf(animation)) }
        verify(exactly = 1) { listener.completed(animate, setOf(animation)) }
        verify(exactly = 1) { onCompleted(animation) }
    }

    @Test fun `animates decreasing number property`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ManualAnimationScheduler() //ImmediateAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()
        val onCompleted        = mockk<(Completable) -> Unit>()

        val outputs = mutableListOf<Float>()

        animate.listeners += listener

        val animation = animate (1f to 0f, tweenFloat(linear, 3 * milliseconds)) {
            outputs += it
        }.apply {
            completed += onCompleted
        }

        animationScheduler.runToCompletion()

        expect(listOf(1f, 1f - 2f/3, 0f)) { outputs }

        verify(exactly = 2) { listener.changed  (animate, setOf(animation)) }
        verify(exactly = 1) { listener.completed(animate, setOf(animation)) }
        verify(exactly = 1) { onCompleted(animation) }
    }

    @Test fun `animates second batch of number properties`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ImmediateAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()
        val onCompleted        = mockk<(Completable) -> Unit>()

        var outputs = mutableListOf<Float>()

        animate.listeners += listener

        val animation1 = animate(0f to 1f, tweenFloat(linear, 3 * milliseconds)) {
            outputs.plusAssign(it)
        }.apply {
            completed += onCompleted
        }

        expect(listOf(0f, 2f/3, 1f)) { outputs }

        verify(exactly = 2) { listener.changed  (animate, setOf(animation1)) }
        verify(exactly = 1) { listener.completed(animate, setOf(animation1)) }
        verify(exactly = 1) { onCompleted(animation1) }

        outputs = mutableListOf()

        val animation2 = animate(0f to 1f, tweenFloat(linear, 3 * milliseconds)) {
            outputs.plusAssign(it)
        }

        expect(listOf(0f, 2/3f, 1f)) { outputs }

        verify(exactly = 2) { listener.changed  (animate, setOf(animation2)) }
        verify(exactly = 1) { listener.completed(animate, setOf(animation2)) }
    }

    @Test fun `animates measure property`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ImmediateAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()
        val onCompleted        = mockk<(Completable) -> Unit>()

        val outputs = mutableListOf<Measure<Time>>()

        animate.listeners += listener

        val animation = animate(0 * hours to 1 * hours, tween(hours, linear, 3 * milliseconds)) {
            outputs += it
        }.apply {
            completed += onCompleted
        }

        expect(listOf(0 * hours, 1 * hours * (2.0/3.0).toFloat(), 1 * hours)) { outputs }

        verify(exactly = 2) { listener.changed  (animate, setOf(animation)) }
        verify(exactly = 1) { listener.completed(animate, setOf(animation)) }
        verify(exactly = 1) { onCompleted(animation) }
    }

    @Test fun `cancels number animation`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ManualAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()

        val outputs1 = mutableListOf<Float>()
        val outputs2 = mutableListOf<Float>()

        animate.listeners += listener

        val animation1 = animate(0f to 1f, tweenFloat(linear, 3 * milliseconds)) { outputs1 += it }
        val animation2 = animate(0f to 1f, tweenFloat(linear, 3 * milliseconds)) { outputs2 += it }

        animation1.cancel()

        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()

        expect(true) { outputs1.isEmpty() }
        expect(listOf(0f, 2/3f)) { outputs2 } // Only the first 2 frames ticks, which begins animation2 and notifies with the first value

        verify(exactly = 1) { listener.changed  (animate, setOf(animation2)         ) }
        verify(exactly = 0) { listener.changed  (animate, match { animation1 in it }) }
        verify(exactly = 0) { listener.completed(animate, any()                     ) }
        verify(exactly = 1) { listener.canceled (animate, setOf(animation1)         ) }
    }

    @Test fun `animation block cancels nested`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ManualAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()

        val outputs1 = mutableListOf<Float>()
        val outputs2 = mutableListOf<Float>()

        animate.listeners += listener

        var animation1 = null as Animation<Float>?
        var animation2 = null as Animation<Float>?

        val topLevel = animate {
            animation1 = 0f to 1f using tweenFloat(linear, 3 * milliseconds).invoke { outputs1 += it }

            animate {
                animation2 = 0f to 1f using tweenFloat(linear, 3 * milliseconds).invoke { outputs2 += it }
            }
        }

        topLevel.cancel()

        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()

        expect(true) { outputs1.isEmpty() }
        expect(true) { outputs2.isEmpty() }

        verify(exactly = 0) { listener.changed  (animate, any  (                          )) }
        verify(exactly = 0) { listener.completed(animate, any  (                          )) }
        verify(exactly = 1) { listener.canceled (animate, setOf(animation1!!, animation2!!)) }
    }

    @Test fun `cancels number animation group`() {
        val timer              = MonotonicTimer()
        val animationScheduler = ManualAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()

        val outputs1 = mutableListOf<Float>()
        val outputs2 = mutableListOf<Float>()

        animate.listeners += listener

        val animations = animate {
            0f to 1f using tweenFloat(linear, 3 * milliseconds).invoke { outputs1 += it }
            0f to 1f using tweenFloat(linear, 3 * milliseconds).invoke { outputs2 += it }
        }

        animations.cancel()

        animationScheduler.runOutstandingTasks()

        expect(true) { outputs1.isEmpty() }
        expect(true) { outputs2.isEmpty() }

        verify(exactly = 0) { listener.changed  (animate, any()) }
        verify(exactly = 0) { listener.completed(animate, any()) }
        verify(exactly = 1) { listener.canceled (animate, match { it.size == 2 }) }
    }

    @Test fun `delay on repeat`() {
        val timer              = MonotonicTimer(increment = 1 * milliseconds)
        val animationScheduler = ManualAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()

        val outputs = mutableListOf<Float>()

        animate.listeners += listener

        animate(0f to 1f, repeat(tweenFloat(linear, 3 * milliseconds), delay = 3 * milliseconds)) { outputs += it }

        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()

        expect(listOf(0f)) { outputs }

        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()

        expect(listOf(0f, 1f/3)) { outputs }
    }

    @Test fun `delay on loop`() {
        val timer              = MonotonicTimer(increment = 1 * milliseconds)
        val animationScheduler = ManualAnimationScheduler()
        val animate            = AnimatorImpl(timer, animationScheduler)
        val listener           = mockk<Listener>()

        val outputs = mutableListOf<Float>()

        animate.listeners += listener

        animate(0f to 1f, loop(tweenFloat(linear, 3 * milliseconds), delay = 3 * milliseconds)) { outputs += it }

        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()

        expect(listOf(0f)) { outputs }

        animationScheduler.runOutstandingTasks()
        animationScheduler.runOutstandingTasks()

        expect(listOf(0f, 1f/3)) { outputs }
    }

    private fun <T> Animation<T>.onCompleted(animate: Animator, outputs: MutableSet<Animation<*>>, block: Animator.AnimationBlock.() -> Unit): Animation<T> = this.apply {
        completed += {
            outputs += animate.invoke(block)
        }
    }
}