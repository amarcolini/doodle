package io.nacular.doodle.geometry

import io.nacular.doodle.utils.RotationDirection
import io.nacular.doodle.utils.RotationDirection.Clockwise
import io.nacular.doodle.utils.RotationDirection.CounterClockwise
import io.nacular.measured.units.Angle
import io.nacular.measured.units.Angle.Companion.cos
import io.nacular.measured.units.Angle.Companion.degrees
import io.nacular.measured.units.Angle.Companion.sin
import io.nacular.measured.units.Measure
import io.nacular.measured.units.normalize
import io.nacular.measured.units.sign
import io.nacular.measured.units.times
import kotlin.jvm.JvmInline
import kotlin.math.min

/**
 * Represents a path-command string as defined by: https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/d#Path_commands
 */
@JvmInline
public value class Path internal constructor(public val data: String) {
    public operator fun plus(other: Path): Path = Path(data + other.data)
}

/**
 * Provides a way to create [Path]s programmatically.
 */
public sealed interface PathBuilder {
    /**
     * Draws a line from the current point to this one.
     *
     * @param point to end at
     */
    public infix fun lineTo(point: Point): PathBuilder

    /**
     * Draws a cubic Bézier curve from the current point to this one.
     *
     * @param point to end at
     * @param firstHandle location of the first control point
     * @param secondHandle location of th second control point
     */
    public fun cubicTo(point: Point, firstHandle: Point, secondHandle: Point): PathBuilder

    /**
     * Draws a quadratic Bézier curve from the current point to this one.
     *
     * @param point to end at
     * @param handle location of the control point
     */
    public fun quadraticTo(point: Point, handle: Point): PathBuilder

    /**
     * Draws an elliptic curve (described [here](https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial/Paths)) from the current point to this one.
     *
     * @param point to end at
     * @param xRadius of the ellipse
     * @param yRadius of the ellipse
     * @param rotation of the ellipse
     * @param largeArch if the arc should have an arc greater than or less than 180°
     * @param sweep determines if the arc should begin moving at positive angles or negative ones
     */
    public fun arcTo(point: Point, xRadius: Double, yRadius: Double, rotation: Measure<Angle> = 0 * degrees, largeArch: Boolean, sweep: Boolean): PathBuilder

    /**
     * Draws a circular curve (described [here](https://developer.mozilla.org/en-US/docs/Web/SVG/Tutorial/Paths)) from the current point to this one.
     *
     * @param point to end at
     * @param radius of the circle
     * @param rotation of the ellipse
     * @param largeArch if the arc should have an arc greater than or less than 180°
     * @param sweep determines if the arc should begin moving at positive angles or negative ones
     */
    public fun arcTo(point: Point, radius: Double, rotation: Measure<Angle> = 0 * degrees, largeArch: Boolean, sweep: Boolean): PathBuilder = arcTo(
            point, radius, radius, rotation, largeArch, sweep
    )

    /** Closes the path. */
    public fun close(): Path

    /**
     * Finishes path without closing it.
     */
    public fun finish(): Path
}

/**
 * Creates a Path from the path data string.
 *
 * @param data conforming to https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/d#Path_commands
 * @return the path, or `null` if [data] is blank
 */
public fun path(data: String): Path? = if (data.isNotBlank()) Path(data) else null

/**
 * Creates a Path at the given point and a builder to further define it.
 *
 * @param from the starting point of the path
 * @return a builder to continue defining the path
 */
public fun path(from: Point): PathBuilder = PathBuilderImpl(from)

/**
 * Converts a [Polygon] to a [Path].
 */
public fun Polygon.toPath(): Path = PathBuilderImpl(points[0]).apply {
    points.subList(1, points.size).forEach {
        lineTo(it)
    }
}.close()

/**
 * Converts [Rectangle] with radius to [Path].
 */
public fun Rectangle.toPath(radius: Double): Path = toPath(radius, radius, radius, radius)

/**
 * Converts [Rectangle] with radii to [Path].
 */
@Suppress("LocalVariableName")
public fun Rectangle.toPath(
    topLeftRadius    : Double = 0.0,
    topRightRadius   : Double = 0.0,
    bottomRightRadius: Double = 0.0,
    bottomLeftRadius : Double = 0.0,
): Path {
    val minDimension       = min(width, height) / 2
    val topLeftRadius_     = min(topLeftRadius,     minDimension)
    val topRightRadius_    = min(topRightRadius,    minDimension)
    val bottomRightRadius_ = min(bottomRightRadius, minDimension)
    val bottomLeftRadius_  = min(bottomLeftRadius,  minDimension)

    return PathBuilderImpl(points[0] + Point(topLeftRadius_, 0.0)).apply {
        lineTo(points[1] - Point(min(minDimension, topRightRadius_), 0.0))

        if (topRightRadius_ > 0) {
            arcTo(points[1] + Point(0.0, topRightRadius_), topRightRadius_, largeArch = false, sweep = true)
        }

        lineTo(points[2] - Point(0.0, bottomRightRadius_))

        if (bottomRightRadius_ > 0) {
            arcTo(points[2] - Point(bottomRightRadius_, 0.0), bottomRightRadius_, largeArch = false, sweep = true)
        }

        lineTo(points[3] + Point(bottomLeftRadius_, 0.0))

        if (bottomLeftRadius_ > 0) {
            arcTo(points[3] - Point(0.0, bottomLeftRadius_), bottomLeftRadius_, largeArch = false, sweep = true)
        }

        lineTo(points[0] + Point(0.0, topLeftRadius_))

        if (topLeftRadius_ > 0) {
            arcTo(points[0] + Point(topLeftRadius_, 0.0), topLeftRadius_, largeArch = false, sweep = true)
        }
    }.close()
}

/**
 * Converts an [Ellipse] to a [Path].
 */
public fun Ellipse.toPath(): Path = Point(center.x, center.y - yRadius).let { topPoint ->
    PathBuilderImpl(topPoint).
        arcTo(Point(center.x, center.y + yRadius), xRadius = xRadius, yRadius = yRadius, rotation = 0 * degrees, largeArch = true, sweep = true).
        arcTo(topPoint,                            xRadius = xRadius, yRadius = yRadius, rotation = 0 * degrees, largeArch = true, sweep = true).
        close()
}

/**
 * Creates a circle path. The [direction] flag allows multiple circles to be combined
 * to create circular holes. A [ring] can be created by joining an outer and inner circle
 * with opposite directions.
 *
 * @param center of the circle
 * @param radius of the circle
 * @param direction to draw in
 * @return path representing the circle
 */
public fun circle(center: Point, radius: Double, direction: RotationDirection): Path {
    val sweep = direction == Clockwise

    return path(Point(center.x, center.y - radius)).
        arcTo(Point(center.x, center.y + radius), radius, radius, largeArch = true, sweep = sweep).
        arcTo(Point(center.x, center.y - radius), radius, radius, largeArch = true, sweep = sweep).
        close()
}

/**
 * Creates ring (donut) path.
 *
 * @param center of the torus
 * @param innerRadius of the torus
 * @param outerRadius of the torus
 * @return path representing the ring
 */
public fun ring(center: Point, innerRadius: Double, outerRadius: Double): Path = circle(center, outerRadius, Clockwise) + circle(center, innerRadius, CounterClockwise)

/**
 * Determines how to connect current and end points in a [Path]
 */
public typealias SegmentBuilder = PathBuilder.(current: Point, end: Point) -> Unit

/**
 * Creates a path for a section of a ring (donut) shape. The direction of sweep is
 * controlled by the sign of [end] - [start].
 *
 * @param center of the torus
 * @param innerRadius of the torus
 * @param outerRadius of the torus
 * @param start angle
 * @param end angle
 * @param startCap defining how to cap the starting side of the section
 * @param endCap defining how to cap the ending side of the section
 * @return path representing the ring section
 */
public fun ringSection(
        center     : Point,
        innerRadius: Double,
        outerRadius: Double,
        start      : Measure<Angle>,
        end        : Measure<Angle>,
        startCap   : SegmentBuilder = { _,it -> lineTo(it) },
        endCap     : SegmentBuilder = { _,_  ->            },
): Path {
    val sweep      = (end - start).sign > 0
    val thickness  = outerRadius - innerRadius
    val cosStart   = cos(start)
    val sinStart   = sin(start)
    val cosEnd     = cos(end  )
    val sinEnd     = sin(end  )
    val outerStart = center + Point(outerRadius * cosStart, outerRadius * sinStart)
    val outerEnd   = center + Point(outerRadius * cosEnd,   outerRadius * sinEnd  )
    val innerStart = outerStart - thickness * Point(cosStart, sinStart)
    val innerEnd   = outerEnd   - thickness * Point(cosEnd,   sinEnd  )
    val largeArch  = ((end - start).normalize() `in` degrees) > 180 //(abs(end - start) `in` degrees) % 360.0 > 180.0

    return path(outerStart).
        arcTo(outerEnd, outerRadius, outerRadius, largeArch = largeArch, sweep = sweep).apply {
            startCap(this, outerEnd, innerEnd)
        }.
        arcTo(innerStart, innerRadius, innerRadius, largeArch = largeArch, sweep = !sweep).apply {
            endCap(this, innerStart, outerStart)
        }.
        close()
}

private class PathBuilderImpl(start: Point): PathBuilder {
    private var data = "M${start.x},${start.y}"

    override fun lineTo(point: Point) = this.also {
        data += "L${point.x},${point.y}"
    }

    override fun cubicTo(point: Point, firstHandle: Point, secondHandle: Point) = this.also {
        data += "C${firstHandle.x},${firstHandle.y} ${secondHandle.x},${secondHandle.y} ${point.x},${point.y}"
    }

    override fun quadraticTo(point: Point, handle: Point) = this.also {
        data += "Q${handle.x},${handle.y} ${point.x},${point.y}"
    }

    override fun arcTo(point: Point, xRadius: Double, yRadius: Double, rotation: Measure<Angle>, largeArch: Boolean, sweep: Boolean) = this.also {
        data += "A$xRadius $yRadius ${rotation `in` degrees} ${if (largeArch) 1 else 0} ${if (sweep) 1 else 0} ${point.x},${point.y}"
    }

    override fun close(): Path = Path(data + "Z")

    override fun finish(): Path = Path(data)
}